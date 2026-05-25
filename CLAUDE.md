# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Latte Java's repository-management web app — the UI users sign in to in order to create groups and publish artifacts to the Latte repository. The companion `cli` and `web` modules live as siblings under `../` (see `latte-java/cli`, `latte-java/web`, etc.).

## Documentation

- `docs/design/` — all design documents and specs (filenames prefixed with `YYYY-MM-DD-` creation date)
- `docs/implementation/` — all implementation plans (filenames prefixed with `YYYY-MM-DD-` creation date)

## Worktree

Worktrees should be created in the `.worktrees` directory in the root of the project (see `.gitignore`).

## Build & run

This project is built with `latte` (the Latte build tool, project file is `project.latte`), not Maven/Gradle. Java 25 is required (`.javaversion`).

| Task                      | Command                                                                                                       |
|---------------------------|---------------------------------------------------------------------------------------------------------------|
| Compile + jar             | `latte build`                                                                                                 |
| Run tests                 | `latte test` (depends on `build`)                                                                             |
| Run a single test         | `latte test --test=org.lattejava.app.tests.MainTest`                                                          |
| Run the web server        | `latte run` (boots on `localhost:8080`, main class `org.lattejava.app.Main`)                                  |
| Tailwind watch            | `latte tailwind` (rebuilds `web/static/css/app.css` from `src/main/css/app.css` on changes to `web/**/*.jte`) |
| Start MinIO (test S3)     | `latte minio` (runs a local MinIO container on `:9000` and creates the `latte-test` bucket; needed for tests) |
| Local integration release | `latte int` (publishes to local integration repo)                                                             |
| Refresh IntelliJ module   | `latte idea`                                                                                                  |
| Clean                     | `latte clean`                                                                                                 |

Tests require FusionAuth running locally on `:9013` with the kickstart applied. Start it with Docker Compose from `src/main/fusionauth/`, using the `mailcatcher` profile:

```
cd src/main/fusionauth && docker compose --profile mailcatcher up -d
```

The kickstart provisions: tenant, application `e9fdb985-9173-4e01-9d73-ac2d60d1dc8e`, admin `admin@lattejava.org`, test user `test@lattejava.org` (password `password`), and two email templates (group invitation, set-password). MailCatcher captures all outbound email from FusionAuth (set-password emails, invite emails, etc.) and exposes them at `http://localhost:1080`. The kickstart configures FusionAuth's tenant SMTP to point at the `mailcatcher` service on port 1025 (no auth).

GitHub identity provider: the kickstart provisions a GitHub IDP (UUID `11111111-2222-3333-4444-200000000001`) as an **OpenID Connect** provider pointed at GitHub's OAuth2 endpoints manually — FusionAuth has no native `GitHub` IDP type, and GitHub doesn't publish OIDC discovery, so the endpoints are hard-coded in the kickstart (`https://github.com/login/oauth/authorize`, `https://github.com/login/oauth/access_token`, `https://api.github.com/user`). Claims are mapped from GitHub's `/user` JSON: `uniqueIdClaim=id`, `emailClaim=email`, `usernameClaim=login`. Scope is `read:user user:email read:org` (the last is required for `GroupService.verifyGitHub` to check org membership). The IDP binds to the application as a "Sign in with GitHub" button and uses `linkingStrategy=LinkByEmail`.

Two env vars must be set before `docker compose up` so the kickstart can wire the OAuth credentials:

```
export FUSIONAUTH_APP_GITHUB_CLIENT_ID=<your github oauth client id>
export FUSIONAUTH_APP_GITHUB_CLIENT_SECRET=<your github oauth client secret>
```

Create a GitHub OAuth App at https://github.com/settings/developers with the homepage URL `http://localhost:8080` and the authorization callback `http://localhost:9013/oauth2/callback`. Copy the client ID and secret into the env vars above. If these aren't set the kickstart still applies, but the IDP records empty credentials and GitHub login won't work until you re-apply the kickstart with real values (`docker compose --profile mailcatcher down -v && up -d`).

GitHub-email caveat: `LinkByEmail` resolves an existing FA user by the email returned from GitHub's `/user` endpoint. If your GitHub primary email is set to private, `/user` returns `email: null` and FA rejects the login. Either make your primary email public on GitHub, or change the IDP's `linkingStrategy` to something username-based.

## Database (D1)

Production and dev both run against Cloudflare D1 over the REST API. **Each developer needs their own D1 database in their own Cloudflare account** — there is no local emulator.

### One-time setup

1. Create a Cloudflare D1 database (Cloudflare dashboard → Workers & Pages → D1 → Create). Name it whatever; the committed default in `wrangler.toml` is `latte-app-dev`.
2. Create an API token (User → API Tokens → Create) with `Account.D1:Edit` scope.
3. Add to `~/.config/latte/app/config.properties`:

   ```properties
   d1.baseUrl=https://api.cloudflare.com/client/v4
   d1.accountId=<your account id>
   d1.databaseId=<your database id>
   d1.apiToken=<your api token>
   ```
4. Edit your local `wrangler.toml` and replace the placeholder `database_id` with your real one. Don't commit that change.
5. Apply migrations:

   ```
   cd app
   npx wrangler d1 migrations apply latte-app-dev --remote
   ```

   (Replace `latte-app-dev` with your DB name. Re-run after adding new migrations.)

### Migrations

Schema changes are SQL files in `migrations/` numbered `NNNN_description.sql`. Wrangler tracks applied migrations in the built-in `d1_migrations` table. Apply with `npx wrangler d1 migrations apply <db-name> --remote`.

### Tests + D1

`latte test` requires:

- FusionAuth running locally on `:9013` (existing requirement).
- Network access to your D1 (the test fixture issues `DELETE`/`INSERT` against the real DB before the suite runs).

`MainTest.beforeSuite()` wipes all rows and re-seeds the `org.lattejava` group + an `OWNER` membership for the FA test user.

## Storage (S3-compatible)

Artifact storage is any S3-compatible store, configured through `s3.*` properties (see `org.lattejava.app.s3`). Production uses Cloudflare R2; **tests use a local MinIO** so no real cloud bucket is needed to run the suite. `GroupService.delete` checks the bucket for objects under a group's prefix before allowing a delete, and the publish API mints presigned `PUT` URLs against the same store.

Config keys (`~/.config/latte/app/config.properties` for dev/prod):

```properties
s3.endpoint=https://<accountId>.r2.cloudflarestorage.com   # or http://localhost:9000 for MinIO
s3.region=auto                                             # "auto" for R2; "us-east-1" for MinIO/AWS
s3.bucket=<your bucket name>
s3.accessKeyId=<access key id>
s3.secretAccessKey=<secret access key>
```

`S3HttpClient` parses the scheme and host from `s3.endpoint` and uses path-style addressing (`<endpoint>/<bucket>/<key>`), which works for R2, MinIO, and AWS.

### Tests + S3 (MinIO)

`src/test/resources/config.properties` ships MinIO `s3.*` values (endpoint `http://localhost:9000`, region `us-east-1`, bucket `latte-test`, key/secret `latte-test`/`latte-test-secret`), so the suite runs against a local MinIO. Start it with this project's own target (each project is self-contained), which also creates the `latte-test` bucket:

```
latte minio
```

S3-touching tests (`S3HttpClientTest`, `GroupService` delete checks, and the full `PublishUploadTest` round trip) then talk to MinIO. **Caveat:** these read the same layered config as everything else, so if your `~/.config/latte/app/config.properties` defines `s3.*` it overrides the committed MinIO values — leave `s3.*` out of your personal config (or point it at MinIO) when running tests. Group/delete fixtures use the `test.delete.*` prefix; the upload test uses a unique `org/lattejava/upload-test-*` key and cleans up after itself.

## Architecture

### Wiring (`Main.java`)

`Main` is the composition root. It loads layered config (the per-developer `~/.config/latte/app/config.properties` takes precedence over the committed `src/test/resources/config.properties`), builds the `OIDCConfig` — including an explicit `introspectionEndpoint`, since FusionAuth's discovery document does not advertise one — creates the typed `OIDC<User>` (with `UserService::toUser`), the cookie codec, and the `JTETemplates`, calls `Services.initialize(config)`, and registers routes on a `Web` instance from the sibling `org.lattejava.web` module. (`Main` does not create a `FusionAuthClient`; the services that need it construct their own.)

Routes split into two trees by authentication model:

- `/` redirects to `/app/` (301). Everything under `/app/*` is the **browser UI**, gated by the OIDC session cookie via `oidc.authenticated()`; `/app/groups/*` additionally installs `GroupSecurity` (membership check) with per-route `hasRole(...)` on owner-only actions.
- `/api/*` is the **token-authenticated JSON API** — the bearer access token comes from the `Authorization` header (handled by `oidc.apiAuthenticated()`), with per-route authorization via `oidc.apiAuthorized(...)`. The current endpoint is `POST /api/v1/publish/{groupName}` (see *Publish API* below).

Static files come from `web/static` mounted at `/static`.

### Services

Services are singletons constructed in `Services.initialize(config)`. Validation is never inlined: each service calls a validator in `org.lattejava.app.service.validation` (`GroupValidator`, `MembershipValidator`, `PublishValidator`) and throws `ValidationException`.

- **`UserService.toUser(JWT)`** — pure JWT → `User` mapping, used as the OIDC factory (`OIDC.create(oidcConfig, UserService::toUser)`). Maps `sub` → `userId`, plus `email` and `preferred_username` → `username`. Email is the primary identifier. Note: those two identity claims are **not** in FusionAuth's default access token — the kickstart's `JWTPopulate` lambda injects them (see the FusionAuth section).
- **`GroupService`** — D1-backed group operations via `DatabaseClient`: create (kind-specific verification state), delete (guarded by an empty-S3-prefix check), lookup, owning-group resolution (`findOwningGroup`, used by the publish API), and description updates.
- **`MembershipService`** — group memberships (invite / accept / decline / leave / role change / remove), enriching member rows with FusionAuth user data.
- **`PublishService`** — validates a publish request and returns a presigned S3 `PUT` URL (see *Publish API*).
- **`VerificationService`** — drives group-ownership verification (DNS TXT challenges and GitHub user/org checks) on a scheduled scan.
- **`ViewService`** — assembles the `MainView` chrome (sidebar groups, active nav, theme) that every page renders against, plus the per-page `GroupView`.

### Publish API

`POST /api/v1/publish/{groupName}` issues a short-lived presigned S3 `PUT` URL so the sibling `cli` can upload an artifact. `web`'s `apiAuthenticated()` validates the bearer access token (reactively refreshing via an `X-Refresh-Token` header when needed); `PublishAuthorizer` (an `APIAuthorizer` in `org.lattejava.app.security`) resolves the most-specific registered group owning the namespace and requires the caller to be an `ACTIVE` `OWNER`/`CONTRIBUTOR` of that group, which must be `VERIFIED`. `PublishController` (a `BodyHandler` fed by `JSONBodySupplier`) then has `PublishService` validate that the requested key is within the namespace and mint the URL via `S3HttpClient`/`S3Signer`. Errors render as JSON via `APIExceptionHandler` (installed at `/api`); browser routes still render the HTML error page via `AppExceptionHandler`. Full design: `docs/design/2026-05-22-publish-api-design.md`.

### Domain model (D1-backed)

Groups, memberships, and verifications live in Cloudflare D1 (see "Database (D1)" above). FusionAuth is the system of record for user identity only — there is no FA-side group or membership state. State on the records:

- `Group.state` → `GroupState` (`VERIFIED` | `PENDING` | `FAILED`)
- `Member.state` → `MembershipState` (`PENDING` | `ACTIVE`)
- `Member.role` → `Role` (`CONTRIBUTOR` | `OWNER`)
- `GroupVerification` tracks outstanding DNS TXT challenges (one per pending group).

Records under `org.lattejava.app.model` — `User`, `Group`, `Member`, `MembershipState`, `Role`, `GroupState`, `GroupVerification`, `InviteRequest`, and the publish API's JSON carriers `PublishRequest`/`PublishResponse` — are immutable carriers; templates render the domain ones directly. View-only models live in `org.lattejava.app.model.view` (`MainView`, `GroupView`, `VerificationView`). (`Artifact` and `ActivityEntry` are placeholder carriers for not-yet-wired artifact/activity UI.) SQL CRUD is on `org.lattejava.app.db.DatabaseClient`.

### Templates (`web/`)

Server-side rendering uses JTE 3.x. `Main` constructs `JTETemplates(BASE_DIR=web, build)`; handlers call `templates.html("pages/foo.jte", req, res, Map.of(...))`. Layout is `web/layout/main.jte`, which expects a `MainView` plus `pageTitle`, `activeNav`, `activeGroupId`, and a sidebar group list. Reusable bits live in `web/components/`. Styling is Tailwind v4 via `@tailwindcss/cli`; the compiled output `web/static/css/app.css` is committed-out at runtime by the `tailwind` target.

### Tests (`src/test/java/`)

TestNG. Tests boot a real `Main` (`@BeforeSuite`) and exercise the running server with `WebTest` from `org.lattejava.web`. `OIDCTestFixture` drives the FusionAuth login flow end-to-end — assertions check redirect chains and rendered HTML. There are no mocks of FusionAuth; if FusionAuth isn't running, the suite cannot pass.

### Module system

Both `src/main` and `src/test` are JPMS modules (`module-info.java`). When adding new external dependencies, update `project.latte` **and** the appropriate `module-info.java` `requires` clause. Internal packages used by tests must be `exports`ed (or `opens` to TestNG, as `org.lattejava.app.tests` already does).

## Code conventions

The project enforces specific Java conventions in `.claude/rules/` — read those files before non-trivial edits.