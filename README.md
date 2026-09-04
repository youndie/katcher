# Katcher

[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081.svg)](https://ktlint.github.io/)
[![kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![native](https://img.shields.io/badge/Native-blue?logoColor=white)](https://kotlinlang.org)
[![jvm](https://img.shields.io/badge/JVM-orange?logoColor=white)](https://kotlinlang.org)
[![android](https://img.shields.io/badge/Android-green?logoColor=white)](https://android.com)
[![katcher client](https://reposilite.kotlin.website/api/badge/latest/snapshots/ru/workinprogress/katcher/client?name=client&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/ru/workinprogress/katcher/client)
[![Docker Image Version](https://img.shields.io/badge/server-latest-blue?logo=docker)](https://github.com/youndie/katcher/pkgs/container/katcher)
[![license](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Lightweight, privacy-friendly error tracking service written in Kotlin with a focus on portability and
extremely low overhead.

Unlike traditional monitoring platforms, Katcher runs as a single self-contained binary compiled with Kotlin/Native,
with a built-in HTMX UI and an embedded SQLite database.
No JVM required in production.

![Screenshot](/screenshot.png?raw=true "screenshot")

## Overview

Katcher provides:

- Lightweight crash reporting API
- Automatic grouping of errors by fingerprint (message + stacktrace)
- Error grouping dashboard built with HTMX
- Zero-runtime-dependency deployment via Kotlin/Native
- SQLite storage using sqlx4k for multiplatform database access
- Authentication via reverse proxy (supports OAuth2-Proxy, Traefik, Nginx)
- Dark/light theme, responsive UI

## Tech Stack

### Backend

- Ktor (native server engine)
- Kotlin/Native
- SQLite (sqlx4k)
- kotlinx.serialization, kotlinx.datetime

### Frontend

- HTMX (dynamic navigation without JavaScript frameworks)
- Tailwind CSS
- kotlinx.html server-side templates

Everything is rendered server-side; no bundlers or Node runtime needed in production.

## Authentication

Katcher does not implement its own user login.
Instead, it trusts upstream authentication headers provided by middleware such as:

- oauth2-proxy
- Traefik ForwardAuth
- NGINX auth_request

Katcher reads the following headers:

- `X-Auth-Request-User` — unique user identifier
- `X-Auth-Request-Email` — user email

If these headers are missing, Katcher returns 401 Unauthorized.

This makes it trivial to run behind any SSO provider (Keycloak, Google, GitHub, etc.) without embedding OAuth logic.

## Running server

```shell
docker run -p 8080:8080 \
  -v ./data:/data \
  ghcr.io/youndie/katcher:latest
```

That container has **no sign-in**: with nothing in front of it, every page answers 401, because
Katcher expects the headers above to have been established by somebody else. It is the right shape
for a cluster that already has an SSO, and a dead end on a laptop.

### With sign-in, in one command

```shell
docker compose -f docker/compose.yaml up -d
open http://127.0.0.1:4180        # someone@example.test / katcher-demo-password
```

Three containers and no database to operate: Katcher, an OpenID Connect provider
([shildik](https://github.com/youndie/shildik), keeping its state in a SQLite file as Katcher
does), and oauth2-proxy between them. A fourth runs once to create the realm, the client and the
person, then exits.

Two things it demonstrates beyond "the UI opens":

* **The ingest paths stay open.** `/api/**` and `/mcp` skip the proxy's authentication, because an
  application sending a crash report holds no browser session and an MCP client is a machine —
  both would otherwise be redirected to a sign-in page they cannot complete. It is the same hole
  the Helm chart punches with its bypass routes.
* **Katcher itself is not published.** Only the proxy has a port; the container that trusts
  headers is not reachable except through the thing that sets them.

Every secret in that file is a literal in a public repository and nothing speaks TLS, so it is for
trying the product out and for demonstrating it — not a deployment.

## Reverse Proxy Setup

### Example oauth2-proxy configuration:

The following headers:

- `X-Auth-Request-User`
- `X-Auth-Request-Email`

must be forwarded to Ktor.

### For Traefik:

```yaml 
authResponseHeaders:
  - X-Auth-Request-User
  - X-Auth-Request-Email
```

## AI agents (MCP)

Katcher can expose crashes to coding agents over the
[Model Context Protocol](https://modelcontextprotocol.io), so you can point an agent at a
repository and ask it to look into a crash. It reads the group, its events, breadcrumbs and
context, and can record the pull request that fixes it.

**Off by default.** Without `MCP_TOKEN` the endpoint is not mounted at all — no route, no
secret, nothing to reach.

### Enabling it

| Variable | Purpose |
|---|---|
| `MCP_TOKEN` | Bearer token clients must present. Setting it is what turns the feature on. |
| `MCP_ALLOWED_HOSTS` | Comma-separated hostnames the endpoint may be reached on. **Required when deployed**: the transport's DNS-rebinding protection accepts `localhost` only by default and refuses everything else with `Invalid Host`. |

```shell
docker run -p 8080:8080 -v ./data:/data \
  -e MCP_TOKEN="$(openssl rand -hex 32)" \
  -e MCP_ALLOWED_HOSTS="katcher.example.com" \
  ghcr.io/youndie/katcher:latest
```

With the Helm chart, pass the token at deploy time rather than committing it:

```shell
helm upgrade --install katcher ./charts/katcher --set mcp.token="$MCP_TOKEN"
```

The chart wires it through a `Secret` and fills `MCP_ALLOWED_HOSTS` from `hostname`.

### Behind a reverse proxy

An MCP client is a machine and carries no browser session, so the forward-auth middleware
described above will reject it before Katcher ever sees the request. `/mcp` needs to bypass
that middleware — it authenticates itself with the bearer token instead. The Helm chart
creates this bypass automatically, but only when `mcp.token` is set.

### Connecting a client

```shell
claude mcp add --transport http katcher https://katcher.example.com/mcp \
  --header "Authorization: Bearer $MCP_TOKEN"
```

Use the `local` or `user` scope. Avoid `project` scope — it writes the configuration into
the repository, token included.

### Tools

| Tool | |
|---|---|
| `list_apps` | Applications reporting to this Katcher |
| `list_error_groups` | Crash groups for an application |
| `get_crash_metadata` | Exception type, stack frames, context keys — no free-form text |
| `get_crash_content` | Full stacktrace, context and breadcrumbs |
| `link_fix` | Record the pull request that fixes a group |

### Why the server sometimes refuses

Crash reports are written by whoever holds an app key, and app keys ship inside client
applications. Someone who extracts one can post text designed to give instructions to an
agent rather than describe a failure — the attack demonstrated against another crash
reporter in 2026, which drove coding agents into running attacker-supplied commands.

Katcher therefore screens crash content before returning it, and holds back anything that
reads as an instruction. It also splits reading a crash into two steps: `get_crash_metadata`
returns only structured, identifier-shaped facts, and `get_crash_content` releases the free
text after the agent reports which stack frames it could locate in the repository. Frames
from libraries and frameworks are expected and cause no problem; at least one frame must
belong to your repository.

Neither of these makes untrusted text safe — no server-side check can, because the
limitation is in the models. They narrow the easy path. Run agents with the sandboxing and
approval settings you would use for any tool that reads outside input.

## 🚀 Deployment

Katcher is designed to run on Kubernetes. We provide an official Helm chart.

👉 **[Read the Deployment Guide](charts/katcher/README.md)** to learn how to install Katcher with Helm, configure Traefik Ingress, and set up SSO integration.

## Android integration

Building an Android app? Use these components:

- Android client: [dev/client-android/README.md](dev/client-android/README.md)
- Android Gradle plugin: [dev/android-gradle-plugin/README.md](dev/android-gradle-plugin/README.md)

The Gradle plugin generates required BuildConfig fields (`KATCHER_BUILD_UUID`, `KATCHER_SERVER_URL`, `KATCHER_APP_KEY`) and uploads your ProGuard/R8 mapping file after builds. The Android client reads those fields automatically and reports crashes to `{serverUrl}/api/reports` with offline persistence.

## Sending Errors From Your Application (Kotlin Client)

Katcher includes a tiny built‑in client you can embed directly into your Kotlin project. It uses the standard Ktor
Client.

### Add dependencies

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "WipSnapshots"
        url = uri("https://reposilite.kotlin.website/snapshots")
    }
}

dependencies {
    implementation("ru.workinprogress.katcher:client:$katcher_version")
    //any ktor engine
    implementation("io.ktor:ktor-client-cio:$ktor_version")
}
```

### Configuration

Configuration
Initialize the client once at the start of your application (e.g., in `main()` or your `Application` class). This sets up the configuration and automatically registers global exception handlers.

```kotlin
fun main() {
    Katcher.start {
        // Full URL to your Katcher instance
        remoteHost = "https://katcher.example.com"

        // Project key from the Dashboard
        appKey = "<YOUR_APP_KEY>"

        // Optional metadata
        release = "1.0.0"
        environment = "Production"

        // Enable detailed logs in console (useful for debugging integration)
        isDebug = true
    }

    // Your app logic...
}
```

### Breadcrumbs (Activity Tracking)

Katcher allows you to track user actions leading up to a crash using "Breadcrumbs". These are stored in memory (up to 50 items) and automatically attached to any error report.

```kotlin
// Simple info event
Katcher.addBreadcrumb("User opened Settings")

// Detailed event with type and metadata
Katcher.addBreadcrumb(
    message = "Network request failed",
    type = "http",
    data = mapOf("url" to "/api/login", "code" to "401")
)
```

Breadcrumbs are cleared automatically when `Katcher.start()` is called to ensure each session starts fresh.

### Manual Error Capture

You can manually report caught exceptions. The `catch` method is **non-blocking** (fire-and-forget), so it can be safely called from anywhere without `runBlocking` or coroutine scopes.
```kotlin
try {
    riskyOperation()
} catch (e: Exception) {
    // Captures the exception, stacktrace, and current context
    Katcher.catch(e)
}
```

Katcher automatically captures:

* message
* full stacktrace
* release
* environment
* appKey
* breadcrumbs (activity timeline)

and sends a POST request to:
```https://<remoteHost>/api/reports```

## Why??

- No SaaS fees
- No heavy agents
- No JVM in production
- Full control over your error data
- Tiny memory footprint
- Designed for teams that want self-hosted crash reporting without complexity
