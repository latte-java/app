# Publish API Design

**Created:** 2026-05-22
**Updated:** 2026-05-24
**Status:** Draft (pending review)

## Summary

A token-authenticated JSON endpoint, `POST /api/v1/publish/{groupName}`, that the Latte `cli` calls
to obtain a presigned Cloudflare R2 URL for uploading a single artifact. The endpoint authenticates
the caller's access token (refreshing it if necessary), confirms the caller is authorized to publish
to the target namespace, and returns a short-lived presigned `PUT` URL the client uploads directly
to.

The endpoint lives **outside** the session-gated `/app/*` tree. Browser pages under `/app/*` are
authenticated by the OIDC session cookie; this API is authenticated by OAuth tokens supplied as
request **headers**, handled by the sibling `web` module's API-auth middleware.

### Division of responsibility (who does what)

The `web` module now provides API authentication/authorization plumbing; this app fills in the
application-specific pieces:

| Concern                                                                           | Owner                                                       |
|-----------------------------------------------------------------------------------|-------------------------------------------------------------|
| Extract tokens from the request (`Authorization: Bearer`, `X-Refresh-Token`)      | `web` — `TokenExtractor.Default`                            |
| Validate the access token via introspection; reactively refresh on inactive       | `web` — `OIDC.apiAuthenticated()` / `APIAuthenticated`      |
| Write refreshed tokens back to the response (`X-Access-Token`, `X-Refresh-Token`) | `web` — `TokenWriter.Default`                               |
| Decode the validated access token to a `JWT` and bind it for the request          | `web` — `APIAuthenticated`                                  |
| **Decide whether the caller may publish to the group**                            | **this app** — `PublishAuthorizer implements APIAuthorizer` |
| **Validate the request body and generate the presigned URL**                      | **this app** — `PublishController` + `PublishService`       |

Because `web` owns token handling, this app implements **no** token-introspection/refresh code, and
the response body carries **no** tokens — refreshed tokens (when a refresh happened) are returned by
`web` in the `X-Access-Token` / `X-Refresh-Token` response headers.

## Contract

### Request

`POST /api/v1/publish/{groupName}`

- Path: `{groupName}` — the artifact's namespace in dotted form (e.g. `com.example` or
  `com.example.foo`). Drives authorization. Need not itself be a registered group (see
  *Authorization*). Carried in the path so the `APIAuthorizer` can read it before the body is parsed.
- Headers:
  - `Authorization: Bearer <accessToken>` — the caller's current OAuth access token (a
    FusionAuth-issued JWT). May be expired.
  - `X-Refresh-Token: <refreshToken>` — the caller's OAuth refresh token, used by `web` when the
    access token is no longer active.
  - `Content-Type: application/json`
- Body:

  ```json
  { "fileName": "com/example/1.0.0/lib-1.0.0.jar" }
  ```

  - `fileName` — the **complete** R2 object key, including the namespace path. Used verbatim as the
    presigned object key.

Token header names are the `web` `TokenExtractor.Default` convention; this app does not override the
extractor.

### Success — `200 OK`

```json
{ "url": "https://<accountId>.r2.cloudflarestorage.com/<bucket>/com/example/1.0.0/lib-1.0.0.jar?X-Amz-Algorithm=…&X-Amz-Signature=…" }
```

- `url` — the presigned `PUT` URL. Always present on success.

If `web` performed a reactive refresh while authenticating this request, the response **also** carries
`X-Access-Token` (and `X-Refresh-Token` when rotated) headers, written by `web`'s `TokenWriter`. The
CLI persists those to replace its stored tokens. This app neither sets nor inspects them.

### Errors

Errors are signalled by throwing an exception; the `/api` `APIExceptionHandler` (see *Components*)
renders the response. `web`'s API middleware and body supplier throw `HTTPException` subtypes, and the
app's own failures are app exceptions the handler maps:

| Status | Thrown by                                                   | Condition                                                                                                                                  |
|--------|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `400`  | `BadRequestException` (`JSONBodySupplier`) / `ValidationException` (`PublishValidator`) | Malformed JSON body; or missing `fileName`, `fileName` not within the `{groupName}` prefix, or an unclean key.                              |
| `401`  | `UnauthenticatedException` (`APIAuthenticated`)             | Missing access token, or an inactive token with no/invalid refresh token, or a token that fails to decode.                                 |
| `403`  | `ForbiddenException` (`APIAuthorized`)                      | The `PublishAuthorizer` denied the request (no registered owning group, owning group not `VERIFIED`, or caller not an `ACTIVE` `OWNER`/`CONTRIBUTOR` of the owning group). |
| `500`  | `R2Exception` (`R2HttpClient`)                              | The presigned URL could not be generated (R2 signing error).                                                                               |
| `503`  | `ServiceUnavailableException` (`APIAuthenticated`)          | Introspection could not reach the IdP.                                                                                                     |

Every error response carries a JSON body. There are two shapes:

**`HTTPException`s** (the `400` malformed-body case, `401`, `403`, `503`) and the `R2Exception`-mapped
`500` use the simple shape, written by web's `DEFAULT_RENDERER`:

```json
{ "error": "<ExceptionType>", "message": "<human-readable detail>" }
```

`error` is the exception's simple type name (e.g. `BadRequestException`, `ForbiddenException`); `message`
is its message. For the `R2Exception` `500` the `APIExceptionHandler` substitutes `error` =
`InternalError` and a fixed message that does not leak R2 detail. The `403` `message` is generic (it
does not reveal whether the group exists versus the caller merely lacking a role), preserving the
non-disclosure property even though the response has a body.

**Validation failures** (`ValidationException` → `400`) instead serialize the full `Errors` object, so
the client gets every field and general error with its code and message:

```json
{
  "fieldErrors": {
    "fileName": [
      { "code": "[blank]fileName", "message": "A file name is required." }
    ]
  },
  "generalErrors": []
}
```

The keys are the `Errors` record's fields (`fieldErrors`, keyed by field name; `generalErrors`); each
entry is an `Error` with `code` (the bracketed validation code) and `message`.

## Request flow

```
  ── web: APIAuthenticated (installed at /api) ──────────────────────────
  extract tokens (Authorization: Bearer, X-Refresh-Token)
     access token missing ──▶ 401
     introspect access token
        network error ──▶ 503
        inactive ──▶ refresh with X-Refresh-Token
                        no/invalid refresh ──▶ 401
                        ok ──▶ write X-Access-Token/X-Refresh-Token to response
     decode validated access token to JWT, bind it
  ── web: APIAuthorized(PublishAuthorizer) ─────────────────────────────
  PublishAuthorizer.authorize(req, jwt):
     userId = jwt.sub ; groupName = path attribute
     resolve owning group (most specific registered ancestor-or-exact)
        none ──▶ false (403)
     owning group VERIFIED? ──no──▶ false (403)
     caller ACTIVE OWNER/CONTRIBUTOR of owning group? ──no──▶ false (403)
     ──▶ true
  ── route body supplier: JSONBodySupplier.of(PublishRequest) ───────────
  parse JSON body ──malformed──▶ throw BadRequestException (400)
                   ──empty──▶ null body (handler treats as missing fileName)
  ── this app: PublishController.publish(req, res, body) ────────────────
  validate (PublishValidator): fileName present & within groupName prefix ──fail──▶ throw ValidationException (400)
  presign PUT (R2Client.presignPut) ──error──▶ throw R2Exception (500)
  ──▶ 200 { url }

  (every thrown exception above is caught and rendered as JSON by the /api
   APIExceptionHandler, which wraps this whole chain)
```

### Authentication (handled by `web`)

`OIDC.apiAuthenticated()`, installed at the `/api` prefix, extracts the tokens, introspects the
access token (RFC 7662), reactively refreshes via the refresh token when introspection reports the
token inactive, writes any refreshed tokens to the response headers, decodes the validated access
token to a `JWT` against JWKS, and binds it for the request. This app writes none of this; it only
**consumes** the bound `JWT` in the authorizer. FusionAuth's discovery document does **not** advertise
`introspection_endpoint`, so it is not auto-discovered from the `issuer`; `Main`'s `OIDCConfig` must
set it explicitly:

```java
.introspectionEndpoint(URI.create(config.get("fusionauth.baseUrl") + "/oauth2/introspect"))
```

(`fusionauth.baseUrl` is already a required config key; in dev it is `http://localhost:9013`, giving
`http://localhost:9013/oauth2/introspect`. `OIDCConfig.build()` runs `requireSecureURI` on the
introspection endpoint, which already permits the localhost-http endpoints discovered today.) Without
this, `apiAuthenticated()` throws at construction.

### Authorization — `PublishAuthorizer` (this app)

Implements `org.lattejava.web.oidc.APIAuthorizer`: `boolean authorize(HTTPRequest req, JWT jwt)`.
Installed on the publish route via `OIDC.apiAuthorized(publishAuthorizer)`. Returning `false` yields a
`403`. It runs **after** authentication, so a valid, decoded `JWT` is guaranteed; it runs **before**
the controller, so it must not read the request body.

Inputs:

- `userId` — `jwt.getString("sub")` parsed to a `UUID` (reuse `UserService.toUser(jwt).userId()` for
  consistency with the rest of the app).
- `groupName` — the route-bound `{groupName}` path attribute (`req.getAttribute("groupName")`),
  normalized (trim/lowercase) to match stored group names. As with `GroupSecurity`, the path
  attribute is bound by route matching before middleware runs; this dependency is to be confirmed for
  prefix-installed `APIAuthorized` during implementation.

Decision — two ordered steps; resolving the owning group is independent of and precedes the
membership check:

**1. Resolve the owning group.** Build the candidate set: `groupName` itself plus every dot-boundary
ancestor, **excluding the bare single-segment prefix** — groups cannot be bare TLDs, so a one-label
name like `com` can never be a registered group and is never a candidate
(`com.example.foo.bar` → `["com.example.foo.bar", "com.example.foo", "com.example"]`). A no-dot
short-name group (e.g. `mygroup`) therefore has no ancestors, and the candidate set is just the exact
name `["mygroup"]`. Select the registered group with the **longest** name among the candidates:

```sql
SELECT name, description, state, verification_code, created_at, verified_at
FROM groups
WHERE name IN (?, ?, …)
ORDER BY LENGTH(name) DESC
LIMIT 1
```

This is the *most specific* registered group covering the namespace — the one that "controls all of
its nestings." No registered candidate → deny.

> The existing `DatabaseClient.findAncestorGroup` is **not** reused: it excludes the exact name and
> uses an unordered `LIMIT 1`, so it cannot identify the most-specific owner. A new
> `findOwningGroup(List<String> candidates)` is added, surfaced through
> `GroupService.findOwningGroup(String namespace)`.

**2. Authorize against the owning group.** Resolution must happen *before* membership is consulted:
otherwise a `WHERE … AND member` query could pick a less-specific group the caller belongs to while a
more-specific owning group exists, wrongly granting access. Once the owning group is known:

- It must be `VERIFIED` (a `PENDING`/`FAILED` group cannot receive artifacts) → else deny.
- The caller must have an `ACTIVE` membership row in **that exact group** with role `OWNER` or
  `CONTRIBUTOR` (reuse `MembershipService.findMember(owningGroup.name(), userId)`). The role check is
  a set membership (`OWNER`, `CONTRIBUTOR`) so future roles default to *not* permitted → else deny.

Like `GroupSecurity`/`HasRole`, the decision logic lives in this middleware-side class while the data
access stays in the services.

#### Worked examples

| `{groupName}` (path)  | Registered groups                | Owning group      | `org.example`-only member result                   |
|-----------------------|----------------------------------|-------------------|----------------------------------------------------|
| `org.example.bar.baz` | `org.example`                    | `org.example`     | allowed (if ACTIVE OWNER/CONTRIBUTOR + verified)   |
| `org.example.foo.x`   | `org.example`, `org.example.foo` | `org.example.foo` | `403` — only `org.example.foo` members may publish |
| `org.example`         | `org.example`                    | `org.example`     | allowed                                            |
| `org.examples`        | `org.example`                    | (none)            | `403` — no registered owner                        |
| `net.other.thing`     | `org.example`                    | (none)            | `403` — no registered owner                        |

### Request validation & presign (this app)

`PublishController.publish(req, res, PublishRequest body)` is a `BodyHandler` and runs only after
authentication and authorization pass.

1. **Parse** — done by the route's `JSONBodySupplier.of(PublishRequest.class)` *before* the handler is
   invoked. A malformed body throws `BadRequestException` (→ `400`); an empty body yields a `null`
   `body`, which the handler treats as a missing `fileName` (→ validation `400`). The handler itself
   does no parsing.
2. **Validate** (`PublishValidator`, in `org.lattejava.app.service.validation`, called by
   `PublishService` — validation is never inlined in the service body, per project convention).
   Produces an `Errors`; non-empty throws `ValidationException`, which the `APIExceptionHandler`
   renders as `400` by serializing the full `Errors` object (see *Errors*). Rules:
   - `fileName` present and non-blank.
   - `fileName` is within the namespace: it starts with `groupName.replace('.', '/') + "/"`, binding
     the requested object key to the authorized namespace so a caller authorized for `com.example`
     cannot obtain a URL for `org/other/...`.
   - `fileName` is a clean key: no leading `/`, no empty segments, no `.`/`..` segments (defensive —
     object keys are literal, but odd keys should not be mintable).
3. **Presign** (`R2Client.presignPut`): an AWS SigV4 **query-string** presigned `PUT` URL for the
   `fileName` key against the configured bucket. Differs from the existing
   `R2Signer.authorizationHeader` (header-based SigV4 `GET`):
   - Method `PUT`; payload hash `UNSIGNED-PAYLOAD`; signed headers `host` only.
   - Expiry **15 minutes** (`X-Amz-Expires=900`).
   - Signature carried in the query string (`X-Amz-Algorithm`, `X-Amz-Credential`, `X-Amz-Date`,
     `X-Amz-Expires`, `X-Amz-SignedHeaders`, `X-Amz-Signature`) rather than an `Authorization` header.

   A new `R2Signer.presignedURL(method, host, path, query, accessKeyId, secretAccessKey, expiry, now)`
   does the query-string canonicalization and signing; `R2HttpClient.presignPut(key, expiry)` builds
   and returns the URL `String`. Failure surfaces as `R2Exception`, which the `APIExceptionHandler`
   renders as `500`. Presigning is purely local computation (no network round-trip), so the `500` path
   is effectively limited to misconfiguration/cryptographic failure.
4. **Respond** `200` with `{ url }`, `Content-Type: application/json`, serializing a `PublishResponse`.
   The handler writes only the success response; all error paths above throw and are rendered by the
   `APIExceptionHandler`.

## Components

| Component                                       | Package                                  | Responsibility                                                                                                                                                                             |
|-------------------------------------------------|------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PublishAuthorizer`                             | `org.lattejava.app.security`             | `implements APIAuthorizer`. Resolve owning group + check VERIFIED & ACTIVE OWNER/CONTRIBUTOR membership; return allow/deny. Looks up `GroupService` + `MembershipService` from `Services`. |
| `PublishController`                             | `org.lattejava.app.controller`           | `BodyHandler<PublishRequest>`: orchestrate `PublishService` (looked up from `Services`) and write the `200` JSON. Pure happy path — errors throw and are rendered by `APIExceptionHandler`. Referenced from `Main`. |
| `APIExceptionHandler`                           | `org.lattejava.app.middleware`           | `extends web ExceptionHandler`, installed at `/api`. Renders JSON: web's `DEFAULT_RENDERER` for every `HTTPException`, plus app renderers for `ValidationException` (`400`, full `Errors` object) and `R2Exception` (`500`, generic message). |
| `PublishService`                                | `org.lattejava.app.service`              | Validate (via `PublishValidator`) → presign (via `R2Client`); return the URL. Singleton in `Services`.                                                                                     |
| `PublishValidator`                              | `org.lattejava.app.service.validation`   | `fileName` presence + namespace-containment + clean-key checks → `Errors`.                                                                                                                 |
| `PublishRequest`                                | `org.lattejava.app.model`                | Inbound JSON carrier: `fileName`.                                                                                                                                                          |
| `PublishResponse`                               | `org.lattejava.app.model`                | Outbound JSON carrier: `url`.                                                                                                                                                              |
| `findOwningGroup`                               | `org.lattejava.app.db.DatabaseClient`    | New query: most-specific registered group among candidate names.                                                                                                                           |
| `findOwningGroup`                               | `org.lattejava.app.service.GroupService` | Build candidate set + delegate to `DatabaseClient`.                                                                                                                                        |
| `R2Signer.presignedURL` / `R2Client.presignPut` | `org.lattejava.app.r2`                   | Query-string presigned `PUT` URL generation.                                                                                                                                               |

`PublishRequest`/`PublishResponse` are API JSON carriers, not JTE view models, so they keep plain
domain-style names rather than the `…View` suffix (reserved for template models).

### Wiring (`Main.main()`)

`Services.initialize` constructs `PublishService` (singleton). The `OIDCConfig` builder gains the
explicit `.introspectionEndpoint(...)` call described under *Authentication* (FusionAuth does not
advertise it via discovery). The route sits at a new top-level prefix, **not** inside `/app`. The
`APIExceptionHandler` is installed first (outermost in `/api`, so it wraps the auth middleware and the
route and renders any thrown exception as JSON), then `web`'s API auth; the body supplier and the
publish authorizer are attached to the route:

```java
.prefix("/api", api -> {
  PublishController publish = new PublishController();
  PublishAuthorizer publishAuthorizer = new PublishAuthorizer();
  api.install(new APIExceptionHandler())
     .install(oidc.apiAuthenticated())
     .prefix("/v1/publish", pub ->
         pub.post("/{groupName}", publish::publish, JSONBodySupplier.of(PublishRequest.class), oidc.apiAuthorized(publishAuthorizer)));
})
```

Per the project pattern (see `GroupController`), components do **not** take services as constructor
parameters — `PublishController` looks up `Services.publishService()` and `PublishAuthorizer` looks up
`Services.groupService()` / `Services.membershipService()` in their own constructors. This requires
`Services.initialize` to have run before these are constructed, which it has (it is the first call in
`Main.main()`). `Services.initialize` also constructs and registers `PublishService` (a singleton,
built from `Configuration` like the other services).

`apiAuthenticated()` requires a configured introspection endpoint, set explicitly on the `OIDCConfig`
(see *Authentication*). The precise route-registration calls follow the existing controller pattern
and may adjust as the `web` API stabilizes.

## Module system

`PublishController` is in the already-exported `controller` package; `PublishAuthorizer` goes in the
`security` package (confirm it is exported as `GroupSecurity`'s package is). Confirm during
implementation that `src/main/java/module-info.java` already `requires` the Jackson databind and the
`org.lattejava.jwt` modules used here (both are used elsewhere — `DatabaseClient`, `UserService` — so
no new `requires` is expected), and that `model` is exported as needed.

## Testing

TestNG, booting a real `Main` and exercising the running server with `WebTest`, consistent with the
existing suite. FusionAuth on `:9013` and D1 connectivity are already required.

**Token validation and refreshing are NOT tested here.** Introspection, reactive refresh, the `401`/
`503` paths, and the refreshed-token (`X-Access-Token`/`X-Refresh-Token`) response are entirely
`web`'s behavior and are covered by `web`'s own tests (`APIAuthenticatedTest`, `APIAuthorizedTest`,
`APIAuthIntegrationTest`). This app's tests obtain a **single valid token** purely to get past
authentication so the app-owned logic (authorization, validation, presign) can be exercised; they do
not assert anything about token state, refresh, or the auth failure codes.

- **`R2Signer` presign — pure unit test.** With a fixed `Instant`, key, and credentials, assert the
  generated URL has the expected canonical query parameters and a deterministic `X-Amz-Signature`.
  No network.
- **`PublishAuthorizer` — integration against D1.** Exercise owning-group resolution and the
  membership/state checks directly: most-specific-owner selection (nested registered groups), no
  registered owner, unverified owner, wrong role / non-ACTIVE / non-member. (Can be driven through the
  end-to-end cases below, but a focused test makes the resolution logic easy to pin down.)
- **`PublishController` — end-to-end via `WebTest`.** Use `OIDCTestFixture` to obtain one valid access
  token for the test user, sent as the `Authorization: Bearer` header (the token is just the price of
  admission past `web`'s auth — its validity/refresh is not what these cases test):
  - `200` — `{groupName}` a group the test user owns (the seeded `org.lattejava` group, `VERIFIED`
    with an `OWNER` membership); assert the response `url` targets the requested key.
  - `403` — (a) a namespace with no registered owning group, (b) an owning group the user is not a
    member of, (c) an unverified owning group, (d) a nested registered owning group the user does not
    belong to though they belong to an ancestor.
  - `400` — `fileName` outside the `{groupName}` prefix; missing `fileName`; malformed JSON; unclean
    key (`..` segment).

The seed in `MainTest.beforeSuite()` already provisions the `org.lattejava` group + an `OWNER`
membership for the FA test user, which the `200` case builds on.

## Related change: reject bare-TLD short names in `GroupValidator`

The "groups cannot be bare TLDs" rule that the owning-group resolution relies on must actually be
enforced at group creation. Today `GroupValidator.validate` only checks the TLD for reverse-DNS names
(it requires `segments[0]` to be a real TLD when `segments.length > 1`); a single-segment short-name
group is not checked against the TLD list, so a name like `com` or `io` would currently be accepted
as a short-name group.

Add a check in `GroupValidator.validate`: when the name is a single segment (a short-name group,
`segments.length == 1`) and `tlds.contains(name)`, add a field error and reject. The existing `TLDList`
field is reused, so no new dependency is needed.

- Error key: `[tld]name` (consistent with the existing `[unknownTld]name` style).
- Message (square-bracketed values per the error-message rule): e.g. "The group name [com] is a
  top-level domain (TLD). Short group names cannot be bare TLDs; use a reverse-DNS name such as
  [com.yourorg] instead."

Test additions (alongside the existing `GroupValidator` tests): a bare TLD (`com`) is rejected; a
non-TLD short name (`mygroup`) is still accepted; reverse-DNS validation is unchanged.

This is logically independent of the presign endpoint but is captured here because the publish
authorization's candidate-set reasoning assumes it holds.

## Out of scope

- The actual artifact upload (the client `PUT`s to the presigned URL directly; the API never touches
  artifact bytes).
- Server-side verification that the upload happened or matches a checksum.
- Rate limiting / quota enforcement on presign issuance.
- Listing, overwriting, or deleting published artifacts.
- Token introspection/refresh and refreshed-token response headers (owned by `web`).
