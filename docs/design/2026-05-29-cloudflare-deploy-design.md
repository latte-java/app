# Cloudflare deployment: `app.lattejava.org`

**Status:** Design / approved to plan
**Created:** 2026-05-29

## Goal

Deploy the Latte Java app so it is publicly accessible at `https://app.lattejava.org`,
running on Cloudflare and connected to a **production** Cloudflare D1 database (plus a
production R2 bucket and the existing production FusionAuth).

## Execution approach (important)

This work will be implemented **step-by-step, stopping to confirm after each step.** Each
step is independently verifiable, and several steps depend on out-of-band actions the agent
cannot perform (Docker builds, publishing the sibling `web` module, `wrangler`/Cloudflare
auth, creating cloud resources, editing FusionAuth). We do **not** run the whole sequence
end-to-end and hope. After each step we verify the result (or hand the user the exact command
to run) and explicitly confirm before moving to the next. The "Implementation steps" section
below is ordered to support that cadence.

## Decisions (settled during brainstorming)

- **Compute:** the JVM runs in a **Cloudflare Container**, fronted by a thin Worker, with
  `app.lattejava.org` attached as a Workers **custom domain** (the `lattejava.org` zone is on
  Cloudflare, so the edge cert + DNS are auto-provisioned).
- **FusionAuth:** point at the **existing production** FusionAuth. Deploying FusionAuth is out
  of scope.
- **Image build:** orchestrated by `latte`. `latte bundle` (already implemented) produces a
  fully runnable bundle in `build/bundle`; a new `latte deploy` target ships it via `wrangler`.

## Why this shape

The app is a **JVM HTTP server** (`web.start(port)`, main `org.lattejava.app.Main`). Cloudflare
Workers run only V8/WASM, so the JVM cannot run as a Worker — Cloudflare Containers is the only
native way to host it. The app already reaches **D1 over the REST API**, **R2 over the S3 API**,
and **FusionAuth over OIDC**, so those are network dependencies regardless of where the JVM runs;
"production D1" reduces to *a new database + migrations + REST credentials in config*.

Two recent changes to the codebase simplify the container story dramatically:

1. **`web`'s `Configuration` now ignores config files that don't exist** (`Files.notExists → continue`).
   Combined with its existing env-var-first lookup, the app can be configured entirely through
   environment variables in the container — **no config file and no `Main` code change required.**
2. **`latte bundle`** lays down a self-contained `build/bundle/` (`app.sh`, `lib/*.jar`, `web/`,
   `build/jte-classes`). `app.sh` builds the explicit per-jar module path and launches the app.
   The Docker image therefore needs only a JDK and a copy of the bundle.

## Runtime facts that constrain the design

- **Full JDK 25 required in the runtime image — not a JRE.** Two independent reasons:
  - JTE compiles templates **at runtime** with the in-process `javac`, and derives the compiler
    classpath from `jdk.module.path`. This is why `app.sh` lists each jar explicitly rather than
    passing the `lib/` directory (documented in `app.sh`'s own header comment). The compiler must
    be present.
  - `Main`'s entry point is a Java 25 **instance `main()`** (JEP 512); older runtimes won't boot it.
  - `app.sh` uses `bash` + `set -euo pipefail`, so the base image needs `bash`. `eclipse-temurin:25-jdk`
    satisfies all three.
- **D1 is reached over REST, not the Worker D1 binding.** The running container authenticates to
  the D1 REST API with `d1.accountId` / `d1.databaseId` / `d1.apiToken`. The `[[d1_databases]]`
  binding in `wrangler.toml` is used **only** by the migration CLI, not by the app at runtime.
- **JTE writes generated classes to `build/jte-classes`** (shipped in the bundle); the container
  filesystem must be writable there at runtime (it is — the bundle dir is the workdir).

## Topology

```
app.lattejava.org ──(Workers custom domain, edge TLS)──► thin Worker
        └─► Container DO (class LatteApp, defaultPort 8080) ──► JVM (org.lattejava.app.Main, port 8080)
                 │
                 ├─► D1 REST API     (d1.* config — NOT the Worker binding)
                 ├─► R2 via S3 API   (s3.* config, prod bucket)
                 └─► prod FusionAuth  (OIDC issuer + introspection)
```

The Worker is pure glue: forward every request to a single container instance and return its
response. No application logic moves into JavaScript.

## Deliverables

1. **`Dockerfile`** (trivial, thanks to the bundle):

   ```dockerfile
   FROM eclipse-temurin:25-jdk
   WORKDIR /app
   COPY build/bundle/ /app/
   EXPOSE 8080
   CMD ["bash", "app.sh"]
   ```

   Plus a **`.dockerignore`** so the build context is just `build/bundle/`.

2. **`latte deploy` target** in `project.latte` (`dependsOn: ["bundle"]`) — runs `wrangler deploy`.
   `wrangler` builds the image from the `Dockerfile`, pushes it to its managed registry, deploys
   the Worker, and attaches the custom domain. (This avoids a separate `docker build` + registry-auth
   step and a double build.)

3. **Worker + `wrangler.toml` additions:**
   - thin forwarding Worker (`src/main/worker/` or similar) — a `Container` subclass `LatteApp`
     with `defaultPort = 8080` and a `sleepAfter` idle timeout; the `fetch` handler forwards to a
     single container instance.
   - `[[containers]]` — `class_name = "LatteApp"`, `image = "./Dockerfile"`, `max_instances`.
   - `[[durable_objects.bindings]]` — bound to `LatteApp`.
   - `[[migrations]]` — `new_sqlite_classes = ["LatteApp"]`.
   - `routes` — `custom_domain = true` for `app.lattejava.org`.
   - the existing `[[d1_databases]]` binding stays (migrations only).

4. **Config & secrets** — every `Main.REQUIRED_CONFIG` key delivered as a container environment
   variable. Non-secret values (base URLs, IDs, bucket, region, issuer) as plain `vars`; sensitive
   values via `wrangler secret`, mapped into the container's `envVars`:
   - secrets: `d1.apiToken`, `fusionauth.apiKey`, `fusionauth.clientSecret`,
     `fusionauth.cliClientSecret`, `github.clientSecret`, `s3.secretAccessKey`,
     `web.cookieEncryptionKey`.
   - (Env var names follow `Configuration`'s normalization: `d1.apiToken` → `D1_APITOKEN`, etc.
     Confirm the exact normalization per key when wiring.)

5. **Production resources (one-time setup):**
   - **Prod D1 database** — create; set its `database_id` for the prod env in `wrangler.toml`;
     `npx wrangler d1 migrations apply <prod-db> --remote` (migration `0002` seeds the reserved
     `org.lattejava` group); feed its REST credentials to the container config.
   - **Prod R2 bucket** — create; set prod `s3.*` (R2 endpoint, `region=auto`, bucket, keys).
   - **Prod FusionAuth wiring** — register `https://app.lattejava.org` authorized redirect +
     logout URLs on the FA application; point `fusionauth.issuer/baseUrl/clientId/clientSecret`
     (and the CLI client id/secret) at prod FA.
   - **Prod GitHub OAuth app** — homepage `https://app.lattejava.org`, callback at prod FA's
     `/oauth2/callback`; set `github.clientId` / `github.clientSecret`.

## ⚠️ Critical pre-step: re-integrate `web`

The app depends on the **published** `web-0.3.0-{integration}.jar`, and that jar is what `latte bundle`
copies into `build/bundle/lib/`. The `Configuration` "ignore missing files" fix only reaches the
container if `web` is **re-integrated** (`latte int` in `../web`) *before* `latte bundle` runs here.
If the bundle carries the old `web` jar, the container will hard-fail at startup trying to open the
non-existent `~/.config/latte/app/config.properties`. Verify the bundled `web` jar contains the fix
before deploying.

## Risks to verify during implementation

- **Secure cookies behind edge TLS.** Cloudflare terminates TLS and the container speaks plain HTTP.
  Confirm the `web` OIDC session cookie is marked `Secure` and that the app honors `X-Forwarded-Proto`
  — otherwise the login redirect loops or the session cookie is dropped. This is the most likely
  first-failure.
- **Cold start on scale-to-zero.** The first request after idle pays JVM boot **plus** JTE template
  compilation. Tune `sleepAfter` and/or keep a warm instance; measure before deciding.

## Minor / optional app changes

- Add `fusionauth.cliClientId` and `fusionauth.cliClientSecret` to `Main.REQUIRED_CONFIG` — they are
  read by `apiConfig` but not validated, so a missing value silently nulls the token API auth instead
  of failing fast at startup.
- Port stays hardcoded at `8080` (matches the Worker `defaultPort`); make it config-driven only if a
  need arises.

## Implementation steps (each followed by a confirm/verify stop)

1. **Re-integrate `web`** (`latte int` in `../web`) and verify the bundled jar carries the
   `Configuration` fix. *Confirm before continuing.*
2. **Add the `Dockerfile` + `.dockerignore`**; build the image locally against `build/bundle` and
   smoke-test the container with env-var config pointed at dev resources. *Confirm it boots and serves.*
3. **Add the Worker + `wrangler.toml` container/DO/migrations/route config.** *Confirm config review.*
4. **Provision prod resources** (D1, R2) and **apply D1 migrations**. *Confirm resources + schema.*
5. **Wire prod FusionAuth + GitHub OAuth** (redirect/logout URLs, client creds). *Confirm in FA.*
6. **Set `vars` + `wrangler secret`s** for all required config keys. *Confirm secret inventory.*
7. **Add the `latte deploy` target** and do a first deploy. *Confirm the Worker + container are live.*
8. **Attach the `app.lattejava.org` custom domain** and verify end-to-end (login via FA, GitHub
   connect, a group operation hitting D1, a publish hitting R2). *Confirm full smoke test.*
9. **Validate the risks** (secure-cookie/`X-Forwarded-Proto`, cold-start behavior) and tune
   `sleepAfter`. *Confirm and close out.*

## Out of scope

FusionAuth deployment, CI/CD pipeline, observability/alerting, multi-region, autoscaling beyond a
single instance.
