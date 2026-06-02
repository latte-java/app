# Latte Java — Repository Management App

The web app users sign in to in order to create groups and publish artifacts to the Latte Java
repository. It doubles as the **reference application** for the Latte framework, exercising the
sibling `web` (routing, middleware, OIDC + token API auth, JTE templating), `http`, and `jwt`
modules, and integrating with the `cli`.

## What it demonstrates

- **Routing & middleware** — prefix-grouped routes, OIDC session auth for the browser UI, and
  bearer-token auth for a JSON API (`web`'s `apiAuthenticated`/`apiAuthorized`).
- **Server-side rendering** — JTE 3 templates with a component library, styled with Tailwind v4.
- **Identity** — FusionAuth via OpenID Connect (plus a "Sign in with GitHub" identity provider).
- **Data & storage** — PostgreSQL via jOOQ for groups/memberships (local Postgres for dev/tests,
  PlanetScale in production), and an S3-compatible object store (Cloudflare R2 in production, MinIO
  for tests) with presigned-URL artifact publishing.

## Tech stack

- **Java 25** (JPMS modules) — see `.javaversion`.
- **Latte build tool** — `project.latte`, not Maven/Gradle.
- JTE 3 · Tailwind v4 · FusionAuth · PostgreSQL + jOOQ · R2 / MinIO.

## Quick start

```bash
latte build      # compile + jar
latte run        # run the server on http://localhost:8080  (main: org.lattejava.app.Main)
latte test       # run the test suite (depends on build)
latte database   # create/recreate the local Postgres app/app_test databases + load schema
latte minio      # start a local MinIO container for S3 tests
latte tailwind   # rebuild CSS from src/main/css/app.css on template changes
```

Running and testing need some local services and per-developer config (FusionAuth on `:9013`, a
local PostgreSQL, an S3 store, and a `~/.config/latte/app/config.properties`). The full,
step-by-step setup — FusionAuth kickstart, Postgres + jOOQ codegen, R2/MinIO, and GitHub OAuth — lives in
[`CLAUDE.md`](CLAUDE.md).

## Project layout

```
src/main/java/org/lattejava/app/
  controller/   HTTP handlers (browser pages + the publish API)
  service/      business logic (singletons; validation in service/validation)
  security/     auth middleware (GroupSecurity, HasRole, PublishAuthorizer)
  db/           jOOQ-backed DatabaseService + generated classes (db/jooq)
  s3/           S3-compatible client + AWS SigV4 signer (R2 / MinIO / AWS)
  model/        immutable domain + view records
  middleware/   exception handlers (HTML for the UI, JSON for the API)
web/            JTE templates, components, and static assets
src/main/fusionauth/   FusionAuth docker-compose + kickstart
src/main/sql/   PostgreSQL schema.sql + seed.sql (source of truth for jOOQ codegen)
docs/           design specs (docs/design) and implementation plans (docs/implementation)
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — architecture, conventions, and complete local setup.
- [`docs/design/`](docs/design) — design specs (e.g. the publish API).
- [`docs/implementation/`](docs/implementation) — implementation plans.

## License

MIT — see [`LICENSE`](LICENSE).
