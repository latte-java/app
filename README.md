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
latte build         # compile + jar
latte run           # run the server on http://localhost:8080  (main: org.lattejava.app.Main)
latte test          # run the test suite (depends on build)
latte main-database # create/recreate the local Postgres app databases (empty; the app migrates on startup)
latte test-database # create/recreate the local Postgres app_test databases (empty; the app migrates on startup)
latte minio         # start a local MinIO container for S3 tests
latte tailwind      # rebuild CSS from src/main/css/app.css on template changes
```

## Running locally

In order to run this application on your local machine, you need to install a couple of things and configure your machine for TLS. Follow these steps to accomplish all of that:

First, edit your `/etc/hosts` file and ensure it has these entries:

```text
127.0.0.1       localhost app.local.lattejava.org auth.local.lattejava.org
```

Next, install the necessary software:

* PostgreSQL

Follow these steps to set up certificates for FusionAuth and the app itself:

1. Install `mkcert` and install the CA using `mkcert -install`
2. `mkdir keys` to create the keys directory
3. `cd keys`
4. `mkcert app.local.lattejava.org`
5. `cd ../src/main/fusionauth`
6. `mkdir certs`
7. `cd certs`
8. `mkcert auth.local.lattejava.org`
9. `cd ..`
10. `cp .env.template .env`
11. `docker compose --profile mailcatcher up -d`

Start the S3 Docker container MinIO for testing by running this command from the root of the project:

`latte minio`

And then you can start the application like this:

`latte clean run`

The application should now be available at https://app.local.lattejava.org:8443.

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
src/main/resources/db/   SQL migrations (<semver>.sql), applied by the app on startup
src/main/sql/   one-off scripts (versions-table bootstrap for pre-migration databases)
docs/           design specs (docs/design) and implementation plans (docs/implementation)
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — architecture, conventions, and complete local setup.
- [`docs/design/`](docs/design) — design specs (e.g. the publish API).
- [`docs/implementation/`](docs/implementation) — implementation plans.

## License

MIT — see [`LICENSE`](LICENSE).
