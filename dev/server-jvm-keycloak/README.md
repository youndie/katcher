# Katcher JVM (Keycloak/OAuth2) — Development Server

This module contains a JVM-based Katcher server implementation intended for development and demos. 
It targets the JVM (Ktor JVM engine) and relies on an OAuth2/OpenID Connect provider (e.g., Keycloak) for authentication.
Data is stored in a local SQLite database via the Exposed ORM.

Unlike the production Kotlin/Native server in the `server` module, this variant is convenient for:
- Rapid local iteration and easier debugging on the JVM
- Trying out Keycloak or any OIDC/OAuth2 provider without a reverse proxy
- Verifying DB schema and queries using Exposed on SQLite

## Features
- JVM target using Ktor
- OAuth2/OIDC bearer token authentication (Keycloak-first, works with other providers)
- SQLite for persistence (via Exposed ORM)
- Similar API surface as the native server (e.g., `POST /api/reports`)

## Requirements
- JDK 21+
- SQLite (embedded via JDBC — no external server needed)
- A running OAuth2/OIDC provider (Keycloak recommended)

## Quick start
1. Start a local Keycloak (or use an existing one).
2. Configure a realm and a client for machine-to-machine access (client credentials), or use any flow suitable to obtain an access token.
3. Clone this repository and open in your IDE.
4. From IDE or Gradle, run the JVM server main entrypoint of this module.
   - Example (may vary based on your Gradle setup):
     - From terminal: `./gradlew :dev:server-jvm-keycloak:run`
     - Or run the main class from the IDE (IntelliJ IDEA).

> Note: The exact Gradle task name can differ depending on the Application plugin configuration. If `run` is not configured, use your IDE to start the server main class.

## Configuration
Set the following environment variables (or system properties) before starting the server:

- `HTTP_PORT` — Port to bind the HTTP server (default: `8080`).
- `DB_PATH` — Path to SQLite file (default: `./data/local.db`).
- `KC_URL` — Your Keycloak server URL, e.g., `https://localhost:8081`.
- `KC_REALM` — Name of your Keycloak realm.
- `KC_CLIENT_ID` — Client ID configured in Keycloak.
- `KC_SECRET` — Client secret for your Keycloak client.
- `HOSTNAME` — The base URL where your app is hosted, for callback URLs.

## Database
- Storage: SQLite
- Access: Exposed ORM
- Location: controlled by `DATABASE_FILE` (default `./data/local.db`)

The server will create the DB file and apply the basic schema on the first run. You can inspect the DB using any SQLite browser.

## Example: send a report with curl
1. Obtain an access token from Keycloak/OIDC (client credentials or another flow).
2. Send:
   ```bash
   curl -X POST "http://localhost:8080/api/reports" \
     -H "Content-Type: application/json" \
     -d '{
       "appKey": "dev-app",
       "message": "Something went wrong",
       "stacktrace": "Example stack trace...",
       "release": "1.0.0",
       "environment": "Dev"
     }'
   ```

## Disclaimer
This module is intended for local development/demos. For production, use the Kotlin/Native server (`server` module) with a reverse proxy and SSO as documented in the root README.
