# Katcher

Katcher is a lightweight, privacy-friendly error tracking service written in Kotlin with a focus on portability and
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

## Sending Errors From Your Application (Kotlin Client)

Katcher includes a tiny built‑in client you can embed directly into your Kotlin project. It uses the standard Ktor Client.

### Add dependencies


```kotlin
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/youndie/katcher")
    }
}

dependencies {
    implementation("ru.workinprogress.katcher:client:$katcher_version")
    //any ktor engine
    implementation("io.ktor:ktor-client-cio:$ktor_version")}
```

### Configuration

Before sending any errors, configure the client once:

```kotlin
with(Katcher) {
    remoteHost = "katcher.example.com"   // your server hostname
    appKey = "<YOUR_APP_KEY>"            // key from the Dashboard
    release = "1.0.0"                    // optional
    environment = "Prod"                 // optional
}
```

### Sending an Error

To report an exception:

```kotlin
try {
    riskyCode()
} catch (t: Throwable) {
    Katcher.catch(t)
}
```

Katcher automatically captures:
* message
* full stacktrace
* release
* environment
* appKey

and sends a POST request to:
```https://<remoteHost>/api/reports```

## Why??

- No SaaS fees
- No heavy agents
- No JVM in production
- Full control over your error data
- Tiny memory footprint
- Designed for teams that want self-hosted crash reporting without complexity
