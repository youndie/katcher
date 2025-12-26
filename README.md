# Katcher

[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081.svg)](https://ktlint.github.io/)
[![kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![native](https://img.shields.io/badge/Native-blue?logoColor=white)](https://kotlinlang.org)
[![jvm](https://img.shields.io/badge/JVM-orange?logoColor=white)](https://kotlinlang.org)
[![katcher client](https://reposilite.kotlin.website/api/badge/latest/snapshots/ru/workinprogress/katcher/client?name=client&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/ru/workinprogress/katcher/client)
[![Docker Image Version](https://img.shields.io/badge/server-latest-blue?logo=docker)](https://github.com/youndie/katcher/pkgs/container/katcher)

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

## 🚀 Deployment

Katcher is designed to run on Kubernetes. We provide an official Helm chart.

👉 **[Read the Deployment Guide](charts/katcher/README.md)** to learn how to install Katcher with Helm, configure Traefik Ingress, and set up SSO integration.

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
import ru.workinprogress.katcher.Katcher

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

and sends a POST request to:
```https://<remoteHost>/api/reports```

## Why??

- No SaaS fees
- No heavy agents
- No JVM in production
- Full control over your error data
- Tiny memory footprint
- Designed for teams that want self-hosted crash reporting without complexity
