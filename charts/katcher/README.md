## Deploying to Kubernetes with Helm

Katcher ships with a Helm chart designed for easy deployment on Kubernetes using [Traefik](https://traefik.io/) as the Ingress controller.

### 1. Install with sign-in included

Katcher has no login of its own: it trusts headers set by whatever runs in front of it. If you have
an SSO already, skip to [Bring your own SSO](#3-bring-your-own-sso). If you do not, the release can
carry one — the provider ([shildik](https://github.com/youndie/shildik), storing
its state in a SQLite file like Katcher does), an oauth2-proxy in front of Katcher, and a Job that
creates the realm, the client and the first person.

It is **off by default**, and that is deliberate rather than a preference about which path is
normal: an installation that already has an SSO must not acquire a second identity provider by
upgrading. With it off, the manifests this chart renders are unchanged to the byte — CI checks
exactly that.

The provider needs a hostname of its own (an OIDC provider serves its paths at the root of a host),
and one Secret, because none of this may live in values:

```shell
kubectl create secret generic katcher-auth -n katcher \
  --from-literal=masterKeys="$(openssl rand -base64 32)" \
  --from-literal=bootstrapToken="$(openssl rand -hex 16)" \
  --from-literal=clientSecret="$(openssl rand -hex 24)" \
  --from-literal=cookieSecret="$(openssl rand -base64 24 | head -c 32)" \
  --from-literal=initialPassword='at-least-twelve-characters'
```

```shell
helm dependency build ./charts/katcher

helm upgrade --install katcher ./charts/katcher -n katcher --create-namespace \
  --set hostname=katcher.example.com \
  --set shildik.enabled=true \
  --set shildik.issuer=https://id.example.com \
  --set shildik.ingress.host=id.example.com \
  --set auth.initialUserEmail=you@example.com
```

What that changes in the release:

* the UI route goes to the proxy instead of to Katcher with a middleware attached — authentication
  happens inside that service rather than beside it;
* the ingest and MCP routes still bypass it, exactly as they bypass an external SSO;
* one Secret serves the provider, the proxy and the Job. `masterKeys` encrypts the signing keys:
  lose it and every token you have issued becomes unverifiable.

Two limits worth knowing before choosing this over your own SSO. The provider runs a **single
replica** on a `ReadWriteOnce` volume — one SQLite file has one writer — so upgrades cost a few
seconds during which nobody can sign in. And the people who may sign in live in that volume: it is
yours to back up, and its backup is the whole volume rather than a copy of the `.db` file, because
the newest writes sit in the WAL until a checkpoint.

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

   # Your own middleware, when you are not using the bundled provider — see "Bring your own SSO"
   middlewares:
      - auth-auth-mw
```
### 3. Bring your own SSO

With `shildik.enabled=false` — the default — nothing in the release signs anybody in, and the
installation you already have is the one that does. Katcher is agnostic about which: it relies on a
"Trusted Handoff" architecture:

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

Then install with that middleware named:
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
### How it works

The Helm chart creates distinct **IngressRoutes**:
1. **The UI Route** (`/`): protected. With the bundled provider it goes to the oauth2-proxy that comes with the release; with your own SSO it goes to Katcher with your middleware attached. Either way somebody has established who the person is, and Katcher reads the injected headers to identify them.
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
