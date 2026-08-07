## Deploying to Kubernetes with Helm

Katcher ships with a Helm chart designed for easy deployment on Kubernetes using [Traefik](https://traefik.io/) as the Ingress controller.

### 1. Prerequisites (Authentication)

Katcher is designed to be lightweight and agnostic to your authentication provider. It relies on a "Trusted Handoff" architecture:

1.  It sits behind your existing auth layer (SSO, OAuth2 Proxy, Keycloak, etc.).
2.  It expects the ingress controller to handle authentication.
3.  It reads user details from trusted HTTP headers.

Before installing, ensure you have a **Traefik Middleware** (e.g., connected to `oauth2-proxy`) that authenticates requests and forwards the following headers:

* `X-Auth-Request-User` (User identifier)
* `X-Auth-Request-Email` (User email)

#### Example: Middleware Configuration

Here is an example `Middleware` resource connecting Traefik to `oauth2-proxy`:

```yaml
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: auth-auth-mw
  namespace: auth
spec:
  forwardAuth:
    address: [http://oauth2-proxy.auth.svc.cluster.local:4180](http://oauth2-proxy.auth.svc.cluster.local:4180)
    trustForwardHeader: true
    authResponseHeaders:
      - X-Auth-Request-User
      - X-Auth-Request-Email
```
### 2. Configuration (values.yaml)

Create a my-values.yaml file to configure your deployment. Katcher uses SQLite by default, so persistent storage is required.

```yaml
# my-values.yaml

# 1. Your public domain for Katcher
hostname: katcher.example.com

# 2. Image settings
server:
   image: katcher
   version: 0.1.14
   resources:
      requests:
         cpu: "30m"
         memory: "32Mi"
      limits:
         cpu: "1"
         memory: "64Mi"

# 3. Persistence (SQLite Database)
dbPath: /data/local.db
storage:
   class: "local-path" # Change to your cluster's storage class (e.g., standard, gp2)
   size: 512Mi

# 4. AI agents over MCP (optional, off unless a token is set)
mcp:
   # Bearer token MCP clients must present. Do NOT commit a real value:
   # pass it at install time with --set mcp.token=... from your CI secret store.
   # Empty means the endpoint is not created at all.
   token: ""

# 5. Traefik & Auth Integration
traefik:
   # The certResolver defined in your Traefik static config (e.g., 'letsencrypt' or 'cloudflare')
   certResolver: cloudflare

   # The middleware defined in Step 1 that protects the UI
   middlewares:
      - auth-auth-mw
```
### 3. Installation
Install or upgrade the chart using Helm. Point it to your values file:
```shell 
helm upgrade --install katcher ./charts/katcher \
  --namespace katcher --create-namespace \
  -f my-values.yaml
```
Alternatively, you can set values via CLI arguments:
```shell
helm upgrade --install katcher ./charts/katcher \
  -n katcher --create-namespace \
  --set hostname=katcher.example.com \
  --set traefik.authMiddleware.name=auth-auth-mw \
  --set traefik.authMiddleware.namespace=auth
```
**How it works**
The Helm chart creates distinct **IngressRoutes**:
1. **The UI Route** (`/`): Protected by the authMiddleware. Humans accessing the dashboard must log in via your SSO. Katcher reads the injected headers to identify the user.
2. **The API Route** (`/api/reports`): **Publicly accessible** (bypasses the auth middleware). This allows your applications and SDKs to send crash reports without needing an interactive login session.
3. **The MCP Route** (`/mcp`): created **only when `mcp.token` is set**. Also bypasses the auth middleware, because an MCP client is a machine with no browser session and would otherwise be rejected before reaching Katcher. It authenticates with the bearer token instead.

### MCP
Setting `mcp.token` turns on the endpoint coding agents use to read crashes, and creates
three things at once: a `Secret` holding the token, the `MCP_TOKEN` environment variable
read from it, and the ingress route above. Leave it empty and none of them exist.

`MCP_ALLOWED_HOSTS` is filled from `hostname` automatically. It has to match the host
clients actually use — the transport rejects any other with `Invalid Host`.

```shell
helm upgrade --install katcher ./charts/katcher \
  -n katcher --create-namespace \
  --set hostname=katcher.example.com \
  --set mcp.token="$MCP_TOKEN"
```

See the [MCP section of the main README](../../README.md#ai-agents-mcp) for connecting a
client and for what the server screens before returning crash content.
