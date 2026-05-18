# Groups Plan 03b — GitHub Verification

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify `io.github.*` reverse-DNS groups via the user's linked GitHub account. The flow: the current user must have a GitHub IDP link in FusionAuth; the app fetches that link's GitHub access token from FA, calls the GitHub API to check org membership (or personal-account match), and on success flips the group's state to `VERIFIED` and deletes any pending verification row.

**Architecture:** A new `GitHubClient` interface (production HTTP impl `GitHubHttpClient`, test fake) abstracts the GitHub API call so tests can avoid real network. `VerificationService` gains `verifyGitHub(Group, User)`. It queries `FusionAuthClient.retrieveIdentityProviderLink(...)` for the user's GitHub link, hands the token to `GitHubClient`, and dispatches to `/orgs/{org}/members/{username}` for org-suffix groups or `/user` for personal accounts. On 401 from GitHub, the FA link is unlinked so the user can re-link; the verification result is "not yet" (no error thrown, no state change). A route `POST /app/groups/{name}/verify/github` triggers the flow. The `verify.jte` template renders a "Verify with GitHub" button instead of the DNS instructions when the group is `io.github.*`.

**Tech Stack:** JDK `HttpClient` for GitHub API (same pattern as `D1`/`R2`). Jackson for parsing GitHub's JSON responses (already a project dep). Tests use a fake `GitHubClient`.

**Reference design:** `docs/design/2026-05-07-groups.md` — section "GitHub group verification."

**Plan 06b dependency:** GitHub IDP must exist in FA with `read:org user:email read:user` scopes (committed in `80c0860`). Without that, users won't have IDP links to query.

---

## File structure

**Create:**
- `src/main/java/org/lattejava/app/github/GitHubClient.java` — interface with two methods: `String getLogin(String accessToken)` and `MembershipStatus checkOrgMembership(String accessToken, String org, String username)`.
- `src/main/java/org/lattejava/app/github/MembershipStatus.java` — enum: `MEMBER`, `NOT_MEMBER`, `UNAUTHORIZED`.
- `src/main/java/org/lattejava/app/github/GitHubHttpClient.java` — production impl. JDK `HttpClient` + Jackson.
- `src/main/java/org/lattejava/app/github/GitHubException.java` — runtime exception (parallel to `D1Exception`, `R2Exception`).
- `src/test/java/org/lattejava/app/tests/service/GitHubVerificationTest.java` — unit tests using a fake `GitHubClient`.

**Modify:**
- `src/main/java/module-info.java` — `exports org.lattejava.app.github;`.
- `src/test/java/module-info.java` — `opens org.lattejava.app.tests.github to org.testng;` (if you split tests into a new package — using `service` is fine too).
- `src/main/java/org/lattejava/app/Main.java` — no required-config changes (GitHub creds live in FA, not in app config).
- `src/main/java/org/lattejava/app/service/VerificationService.java` — add `verifyGitHub(Group, User)` method; build `GitHubClient` from constructor; add a 3-arg test-only constructor that injects both `DNSResolver` and `GitHubClient`.
- `src/main/java/org/lattejava/app/controller/GroupController.java` — add `verifyGitHub` handler.
- `src/main/java/org/lattejava/app/Main.java` — register `POST /app/groups/{name}/verify/github` route.
- `web/pages/groups/verify.jte` — branch on `GroupValidator.kindOf(group.name())`: render the GitHub flow for `REVERSE_DNS_GITHUB`, DNS flow otherwise.

---

## Decisions locked in for this plan

- **GitHub API base URL:** `https://api.github.com`. Hardcoded — no config knob.
- **`MembershipStatus.MEMBER` semantics:** for an org group (`io.github.<org>.*`), MEMBER means GitHub returned 204 from `GET /orgs/{org}/members/{username}`. For a personal group (`io.github.<user>`, exactly 3 labels), MEMBER means `GET /user` returned a `login` that equals `<user>` (case-insensitive).
- **`UNAUTHORIZED`:** means the FA-stored token was rejected (401). The service responds by unlinking the FA IDP link (so the next login triggers a fresh OAuth flow) and leaves the group state unchanged. The route returns to the verify page with an inline message — no exception thrown to the user.
- **No `group_verifications` row** for GitHub groups (consistent with Plan 03's design note).
- **`verifyGitHub` is invoked when the user clicks the "Verify with GitHub" button.** No background scanner. (Re-verification is out of scope per the design.)
- **In-app "Connect GitHub" link.** If the user has no GitHub IDP link, the verify page shows a "Connect GitHub" button that redirects through `/oidc/login?idp_hint=<github-idp-uuid>&return_to=<verify-url>`. The `web` library's `LoginHandler` (extended in Task 4b) builds the FA `/authorize` URL with proper PKCE/state and writes the `returnTo` cookie. FA runs the GitHub OAuth handshake, attaches the IDP link to the already-authenticated FA user, and `CallbackHandler` redirects back to the verify page using the `returnTo` cookie.
- **State / CSRF / PKCE stay owned by the `web` library.** The app does not generate state, write the state cookie, or compute the PKCE code_challenge — those internals stay where they are. The library accepts two new optional inputs (`idp_hint`, `return_to`); everything else is unchanged.
- **Personal-account `<user>.github.io` distinction.** A reverse-DNS name with exactly 3 labels (`io.github.something`) is treated as a personal account; the third label is the GitHub login to match. With 4+ labels (`io.github.someorg.subproject`), the third label is treated as the GitHub org name to check membership against; the rest of the name is ignored for verification purposes (subgroups are implicit per the design).
- **No retry on 5xx from GitHub.** Single attempt; surface as a generic failure.

---

## Task 1: `GitHubClient` interface + `MembershipStatus` + `GitHubException`

**Files:**
- Create: `src/main/java/org/lattejava/app/github/GitHubClient.java`
- Create: `src/main/java/org/lattejava/app/github/MembershipStatus.java`
- Create: `src/main/java/org/lattejava/app/github/GitHubException.java`

### `MembershipStatus.java`

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

public enum MembershipStatus {
  MEMBER,
  NOT_MEMBER,
  UNAUTHORIZED
}
```

### `GitHubClient.java`

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

/**
 * GitHub API access. Implementations should not throw checked exceptions; transport
 * failures surface as {@link GitHubException}. Authentication failures are conveyed
 * by returning {@link MembershipStatus#UNAUTHORIZED} from {@link #checkOrgMembership}
 * or by {@link #getLogin} returning {@code null}.
 */
public interface GitHubClient {
  /**
   * Checks whether {@code username} is a member of {@code org} via
   * {@code GET /orgs/{org}/members/{username}}.
   *
   * @param accessToken The user's GitHub OAuth token.
   * @param org         The GitHub organization name.
   * @param username    The GitHub login to check.
   * @return MEMBER (HTTP 204), NOT_MEMBER (404), UNAUTHORIZED (401).
   */
  MembershipStatus checkOrgMembership(String accessToken, String org, String username);

  /**
   * Returns the GitHub login for the user owning {@code accessToken}, or {@code null}
   * if the token is invalid (401) or the response can't be parsed.
   */
  String getLogin(String accessToken);
}
```

### `GitHubException.java`

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

public class GitHubException extends RuntimeException {
  public GitHubException(String message) {
    super(message);
  }

  public GitHubException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

### Commit

```bash
git add src/main/java/org/lattejava/app/github/GitHubClient.java
git add src/main/java/org/lattejava/app/github/MembershipStatus.java
git add src/main/java/org/lattejava/app/github/GitHubException.java
git commit -m "feat(github): GitHubClient interface + MembershipStatus + GitHubException"
```

---

## Task 2: `GitHubHttpClient` impl

**File:** `src/main/java/org/lattejava/app/github/GitHubHttpClient.java`

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

import module com.fasterxml.jackson.databind;
import module java.base;
import module java.net.http;

public class GitHubHttpClient implements GitHubClient {
  public static final String BASE_URL = "https://api.github.com";
  private final HttpClient httpClient;
  private final ObjectMapper mapper;

  public GitHubHttpClient() {
    this.httpClient = HttpClient.newHttpClient();
    this.mapper = new ObjectMapper();
  }

  @Override
  public MembershipStatus checkOrgMembership(String accessToken, String org, String username) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/orgs/" + org + "/members/" + username))
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer " + accessToken)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .GET()
        .build();
    HttpResponse<String> response = send(request, "checkOrgMembership", org + "/" + username);
    return switch (response.statusCode()) {
      case 204 -> MembershipStatus.MEMBER;
      case 401 -> MembershipStatus.UNAUTHORIZED;
      case 404, 302 -> MembershipStatus.NOT_MEMBER;
      default -> throw new GitHubException("GitHub /orgs/{org}/members/{user} returned HTTP ["
          + response.statusCode() + "] for [" + org + "/" + username + "]: [" + response.body() + "]");
    };
  }

  @Override
  public String getLogin(String accessToken) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/user"))
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer " + accessToken)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .GET()
        .build();
    HttpResponse<String> response = send(request, "getLogin", "(current user)");
    if (response.statusCode() == 401) {
      return null;
    }
    if (response.statusCode() / 100 != 2) {
      throw new GitHubException("GitHub /user returned HTTP [" + response.statusCode() + "]: [" + response.body() + "]");
    }
    try {
      JsonNode node = mapper.readTree(response.body());
      JsonNode login = node.get("login");
      return login == null || login.isNull() ? null : login.asText();
    } catch (IOException e) {
      throw new GitHubException("Failed to parse GitHub /user response", e);
    }
  }

  private HttpResponse<String> send(HttpRequest request, String op, String detail) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new GitHubException("GitHub [" + op + "] failed for [" + detail + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GitHubException("GitHub [" + op + "] interrupted for [" + detail + "]", e);
    }
  }
}
```

### Module export

In `src/main/java/module-info.java`, add `exports org.lattejava.app.github;` alphabetized.

### Commit

```bash
git add src/main/java/org/lattejava/app/github/GitHubHttpClient.java
git add src/main/java/module-info.java
git commit -m "feat(github): GitHubHttpClient impl + module export"
```

---

## Task 3: `VerificationService.verifyGitHub`

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/VerificationService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java`

### Add fields + constructor

`VerificationService` currently has `DNSResolver` + scheduler. Add `FusionAuthClient` and `GitHubClient` fields.

The class shape changes to (alphabetical fields):

```java
public class VerificationService {
  public static final Duration DEADLINE = Duration.ofHours(48);
  private final DatabaseClient databaseClient;
  private final DNSResolver dnsResolver;
  private final FusionAuthClient fusionAuth;
  private final GitHubClient githubClient;
  private final ScheduledExecutorService scheduler;

  public VerificationService(Configuration config) {
    this(config, new JNDIDNSResolver(), new GitHubHttpClient());
  }

  /** Test-only constructor. */
  public VerificationService(Configuration config, DNSResolver dnsResolver, GitHubClient githubClient) {
    this.databaseClient = new DatabaseClient(config);
    this.dnsResolver = dnsResolver;
    this.fusionAuth = new FusionAuthClient(config.get("fusionauth.apiKey"), config.get("fusionauth.baseUrl"));
    this.githubClient = githubClient;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "verification-scanner");
      t.setDaemon(true);
      return t;
    });
  }
  ...
}
```

Rename the field `resolver` → `dnsResolver` so the GitHub equivalent has a parallel name. Update internal references.

Existing tests construct `VerificationService(config, resolver)` (2-arg) — the new 3-arg replaces that. Update `VerificationServiceTest.beforeClass()`:

```java
service = new VerificationService(config, dnsResolver, new GitHubHttpClient());
```

Or pass a fake `GitHubClient`; for existing DNS-only tests it doesn't matter — they don't exercise `verifyGitHub`.

### Add `verifyGitHub` method

```java
public GitHubVerifyResult verifyGitHub(Group group, User current) {
  if (GroupValidator.kindOf(group.name()) != GroupKind.REVERSE_DNS_GITHUB) {
    Errors errors = new Errors();
    errors.addGeneralError("[notGitHub]group",
        "The group [%s] is not a github.io group.", group.name());
    throw new ValidationException(errors);
  }

  IdentityProviderLink link = retrieveGitHubLink(current.userId());
  if (link == null || link.token == null) {
    return GitHubVerifyResult.NOT_LINKED;
  }

  String[] labels = group.name().split("\\.");
  // labels[0] = "io", labels[1] = "github", labels[2] = login/org, labels[3+] = subgroup tail
  String accountOrOrg = labels[2];
  boolean isPersonal = labels.length == 3;

  if (isPersonal) {
    String login = githubClient.getLogin(link.token);
    if (login == null) {
      unlinkGitHub(current.userId(), link.identityProviderId);
      return GitHubVerifyResult.UNAUTHORIZED;
    }
    if (!login.equalsIgnoreCase(accountOrOrg)) {
      return GitHubVerifyResult.NOT_AUTHORIZED;
    }
  } else {
    String login = githubClient.getLogin(link.token);
    if (login == null) {
      unlinkGitHub(current.userId(), link.identityProviderId);
      return GitHubVerifyResult.UNAUTHORIZED;
    }
    MembershipStatus status = githubClient.checkOrgMembership(link.token, accountOrOrg, login);
    if (status == MembershipStatus.UNAUTHORIZED) {
      unlinkGitHub(current.userId(), link.identityProviderId);
      return GitHubVerifyResult.UNAUTHORIZED;
    }
    if (status != MembershipStatus.MEMBER) {
      return GitHubVerifyResult.NOT_AUTHORIZED;
    }
  }

  databaseClient.updateGroupState(group.name(), GroupState.VERIFIED, Instant.now());
  databaseClient.deleteVerification(group.name());
  return GitHubVerifyResult.VERIFIED;
}

private IdentityProviderLink retrieveGitHubLink(UUID userId) {
  ClientResponse<IdentityProviderLinkResponse, Errors> response =
      fusionAuth.retrieveIdentityProviderLinks(/* identityProviderId */ GITHUB_IDP_ID, userId);
  if (!response.wasSuccessful() || response.successResponse == null
      || response.successResponse.identityProviderLinks == null
      || response.successResponse.identityProviderLinks.isEmpty()) {
    return null;
  }
  return response.successResponse.identityProviderLinks.getFirst();
}

private void unlinkGitHub(UUID userId, UUID idpId) {
  fusionAuth.deleteIdentityProviderLink(idpId, "", userId);
}
```

Add a `GITHUB_IDP_ID` constant matching the kickstart UUID:

```java
private static final UUID GITHUB_IDP_ID = UUID.fromString("11111111-2222-3333-4444-200000000001");
```

Add a `GitHubVerifyResult` enum (or place it in `org.lattejava.app.service` package):

```java
public enum GitHubVerifyResult {
  NOT_LINKED,        // User hasn't linked GitHub
  UNAUTHORIZED,      // Token rejected (401); link was removed
  NOT_AUTHORIZED,    // Not a member of org / login doesn't match
  VERIFIED           // Success — group state flipped
}
```

The exact FA SDK class names (`IdentityProviderLink`, `IdentityProviderLinkResponse`, etc.) may differ; verify against `io.fusionauth.domain.api.IdentityProviderLinkResponse` and adjust.

### Tests

Add to `VerificationServiceTest`:

```java
@Test
public void verifyGitHub_notGitHubKind_throws() {
  Group g = new Group("test.notgithub", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1L), null);
  client.insertGroup(g);
  try {
    User current = new User(UUID.randomUUID(), "u@x", "U");
    expectThrows(ValidationException.class, () -> service.verifyGitHub(g, current));
  } finally {
    client.deleteGroup("test.notgithub");
  }
}
```

E2E tests against real GitHub are out of scope. The `verifyGitHub_notGitHubKind_throws` test confirms the kind-check; everything else requires a real GitHub OAuth round-trip and a real org, which we can't reasonably reproduce in CI.

### Commit

```bash
git add src/main/java/org/lattejava/app/service/VerificationService.java
git add src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java
git commit -m "feat(service): VerificationService.verifyGitHub for io.github.* groups"
```

---

## Task 4: Route + controller handler

**Files:**
- Modify: `src/main/java/org/lattejava/app/controller/GroupController.java`
- Modify: `src/main/java/org/lattejava/app/Main.java`

Add handler:

```java
public void verifyGitHub(HTTPRequest req, HTTPResponse res) {
  String groupName = (String) req.getAttribute("name");
  Optional<Group> groupOpt = groupService.findGroup(groupName);
  if (groupOpt.isEmpty()) {
    res.setStatus(404);
    return;
  }
  User current = oidc.user();
  GitHubVerifyResult result;
  try {
    result = verificationService.verifyGitHub(groupOpt.get(), current);
  } catch (ValidationException e) {
    res.sendRedirect("/app/groups/" + groupName + "/verify", 303);
    return;
  }
  res.sendRedirect("/app/groups/" + groupName + "/verify?status=" + result.name().toLowerCase(), 303);
}
```

The `?status=` query string lets the verify page render the right message after the redirect.

Register the route inside the `prefix("/groups", ...)` block in `Main.java`:

```java
groups.post("/{name}/verify/github", groupController::verifyGitHub);
```

### Commit

```bash
git add src/main/java/org/lattejava/app/controller/GroupController.java
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(controller): POST /app/groups/{name}/verify/github"
```

---

## Task 4b: `web` LoginHandler — accept `idp_hint` and `return_to`

**Repo:** `../web` (sibling module — separate working tree, separate commit).

**Goal:** Make `/oidc/login` accept two optional query params so apps can trigger an authenticated IDP-link round-trip without reimplementing PKCE state from outside the library.

**Why this lives in `web` and not in the app:**

`LoginHandler` (in `web`) generates the OAuth `state` (44 hex chars), uses it as the PKCE `code_verifier`, computes `code_challenge = SHA256(state)` (base64url), writes the `state_cookie`, and builds the `/authorize` URL. Both `Tools.computeCodeChallenge` and `Tools.addTransientCookie` are package-private. The app can't bypass `LoginHandler` without either duplicating PKCE or skipping it, both of which are unacceptable. The minimal correct fix is to teach `LoginHandler` two passthroughs.

**Files:**
- Modify: `web/src/main/java/org/lattejava/web/oidc/internal/LoginHandler.java`
- Modify: `web/src/test/java/org/lattejava/web/tests/oidc/LoginRedirectTest.java` (add tests)

### Behavior changes

1. **`idp_hint` query param** — if present and non-blank, append `&idp_hint=<urlencoded>` to the authorize URL. FA recognizes both IDP names (`GitHub`) and IDP UUIDs.

2. **`return_to` query param** — if present and validates as a safe relative path, write it to the `returnTo` cookie (using `Tools.addTransientCookie` with `config.returnToCookieName()`). `CallbackHandler` already reads that cookie at `CallbackHandler.java:94,99` and redirects there post-exchange.

### `return_to` validation

To prevent open-redirect:

```java
private static boolean isSafeReturnTo(String value) {
  if (value == null || value.isBlank()) return false;
  // Must be a relative path: starts with '/' but not '//' (protocol-relative) or '/\' variants.
  if (!value.startsWith("/")) return false;
  if (value.startsWith("//") || value.startsWith("/\\")) return false;
  return true;
}
```

If validation fails, silently ignore the param (don't 400 — degrade to `postLoginPage`).

### Updated `handle` method

```java
@Override
public void handle(HTTPRequest req, HTTPResponse res) throws Exception {
  byte[] stateBytes = new byte[22];
  RANDOM.nextBytes(stateBytes);
  String state = HexFormat.of().formatHex(stateBytes);
  String codeChallenge = Tools.computeCodeChallenge(state);

  Tools.addTransientCookie(req, res, config.stateCookieName(), state);

  String returnTo = req.getURLParameter("return_to");
  if (isSafeReturnTo(returnTo)) {
    Tools.addTransientCookie(req, res, config.returnToCookieName(), returnTo);
  }

  URI redirectURI = config.fullRedirectURI(req);
  StringBuilder url = new StringBuilder(config.authorizeEndpoint().toString());
  url.append(url.indexOf("?") < 0 ? '?' : '&');
  url.append("response_type=code");
  url.append("&client_id=").append(URLEncoder.encode(config.clientId(), StandardCharsets.UTF_8));
  url.append("&redirect_uri=").append(URLEncoder.encode(redirectURI.toString(), StandardCharsets.UTF_8));
  url.append("&scope=").append(URLEncoder.encode(String.join(" ", config.scopes()), StandardCharsets.UTF_8));
  url.append("&state=").append(state);
  url.append("&code_challenge=").append(codeChallenge);
  url.append("&code_challenge_method=S256");

  String idpHint = req.getURLParameter("idp_hint");
  if (idpHint != null && !idpHint.isBlank()) {
    url.append("&idp_hint=").append(URLEncoder.encode(idpHint, StandardCharsets.UTF_8));
  }

  res.sendRedirect(url.toString());
}
```

### Tests

Add to `LoginRedirectTest`:

```java
@Test
public void login_withIdpHint_appendsToAuthorizeUrl() {
  // GET /oidc/login?idp_hint=11111111-2222-3333-4444-200000000001
  // Assert Location contains idp_hint=11111111-...-200000000001 (urlencoded)
}

@Test
public void login_withSafeReturnTo_writesReturnToCookie() {
  // GET /oidc/login?return_to=%2Fapp%2Fgroups%2Ffoo%2Fverify
  // Assert returnTo cookie set to "/app/groups/foo/verify"
}

@Test
public void login_withAbsoluteReturnTo_ignoresIt() {
  // GET /oidc/login?return_to=https%3A%2F%2Fevil.com
  // Assert returnTo cookie is NOT set
}

@Test
public void login_withProtocolRelativeReturnTo_ignoresIt() {
  // GET /oidc/login?return_to=%2F%2Fevil.com
  // Assert returnTo cookie is NOT set
}
```

### Commit (in `web` repo)

```bash
git add src/main/java/org/lattejava/web/oidc/internal/LoginHandler.java
git add src/test/java/org/lattejava/web/tests/oidc/LoginRedirectTest.java
git commit -m "feat(oidc): LoginHandler supports idp_hint + return_to query params"
```

After committing, publish/install the new `web` snapshot so the app picks it up. (`latte int` in `web/`, then refresh the app's dependency.)

---

## Task 4c: App "Connect GitHub" handler + route

**Goal:** Wire the verify page's "Connect GitHub" button to the new `/oidc/login` query params. After this lands, clicking the button kicks off the FA OAuth handshake with GitHub, FA attaches the IDP link to the already-authenticated FA user, and the user is redirected back to the verify page.

**Files:**
- Modify: `src/main/java/org/lattejava/app/controller/GroupController.java`
- Modify: `src/main/java/org/lattejava/app/Main.java`

### Handler

```java
public static final UUID GITHUB_IDP_ID = UUID.fromString("11111111-2222-3333-4444-200000000001");

public void connectGitHub(HTTPRequest req, HTTPResponse res) {
  String groupName = (String) req.getAttribute("name");
  String returnTo = URLEncoder.encode("/app/groups/" + groupName + "/verify", StandardCharsets.UTF_8);
  String url = "/oidc/login?idp_hint=" + GITHUB_IDP_ID + "&return_to=" + returnTo;
  res.sendRedirect(url, 302);
}
```

Three things to note:
- We don't read `OIDCConfig` here. The login path is `/oidc/login` by convention; if a future config changes it, both the redirect target and the route registration have to update together — but that's already the case for any link to OIDC paths.
- `return_to` is a relative path (passes the `isSafeReturnTo` check in Task 4b).
- `idp_hint` is the GitHub IDP UUID from the kickstart (`githubIdpId`).

### Route

Inside the `prefix("/groups", ...)` block in `Main.java`:

```java
groups.get("/{name}/verify/connect-github", groupController::connectGitHub);
```

`GET` (not POST) so an anchor tag works in the template.

### Tests

```java
@Test
public void connectGitHub_redirectsToOidcLoginWithIdpHintAndReturnTo() {
  // GET /app/groups/test.foo/verify/connect-github
  // Assert Location is "/oidc/login?idp_hint=11111111-...-200000000001&return_to=%2Fapp%2Fgroups%2Ftest.foo%2Fverify"
}
```

Reuse the controller test infrastructure that's already in place for `verifyGitHub`.

### Commit

```bash
git add src/main/java/org/lattejava/app/controller/GroupController.java
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(controller): GET /app/groups/{name}/verify/connect-github (FA IDP handoff)"
```

---

## Task 5: `verify.jte` template branching

**File:** `web/pages/groups/verify.jte`

Branch on `GroupValidator.kindOf(group.name())`:

```jte
@import org.lattejava.app.service.validation.GroupValidator
@import org.lattejava.app.service.GroupKind

!{GroupKind kind = GroupValidator.kindOf(group.name());}

@if(kind == GroupKind.REVERSE_DNS_GITHUB)
  <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">
    This group uses GitHub for verification. We'll check that you're a member of the GitHub account or organization that owns this name.
  </p>
  <div class="flex gap-2">
    <form method="post" action="/app/groups/${group.name()}/verify/github">
      <button type="submit" class="px-4 py-2 text-sm font-medium text-white bg-sky-600 rounded-md hover:bg-sky-700">
        Verify with GitHub
      </button>
    </form>
    <a href="/app/groups/${group.name()}/verify/connect-github"
       class="px-4 py-2 text-sm font-medium text-white bg-slate-800 rounded-md hover:bg-slate-900">
      Connect GitHub
    </a>
  </div>
  !{String status = req == null ? null : (String) req.getAttribute("status");}
  @if("verified".equals(status))
    <p class="mt-3 text-sm text-emerald-700 dark:text-emerald-300">Verified.</p>
  @elseif("not_linked".equals(status))
    <p class="mt-3 text-sm text-amber-700 dark:text-amber-300">Click "Connect GitHub" to link your GitHub account, then try again.</p>
  @elseif("unauthorized".equals(status))
    <p class="mt-3 text-sm text-amber-700 dark:text-amber-300">Your GitHub link expired. Click "Connect GitHub" to re-link.</p>
  @elseif("not_authorized".equals(status))
    <p class="mt-3 text-sm text-red-700 dark:text-red-300">Your GitHub account isn't a member of the org or doesn't match the personal account in this group name.</p>
  @endif
@else
  <!-- existing DNS verification UI -->
@endif
```

(The DNS UI is what the template currently renders for all groups. Wrap it in the `@else` branch.)

The `?status=` query param is read via the request — if `req.getParameter("status")` is the API, use that instead. Adapt to the framework's actual API.

### Commit

```bash
git add web/pages/groups/verify.jte
git commit -m "feat(ui): GitHub verify button on io.github.* groups"
```

---

## Self-review checklist

- `GitHubClient` interface has two methods (`getLogin`, `checkOrgMembership`) returning enum/null instead of throwing on 401?
- `GitHubHttpClient` uses `Authorization: Bearer` + `X-GitHub-Api-Version` headers?
- `VerificationService.verifyGitHub` handles four outcomes: NOT_LINKED, UNAUTHORIZED, NOT_AUTHORIZED, VERIFIED?
- Personal-account branch matches 3-label names; org branch matches 4+ labels?
- UNAUTHORIZED unlinks the FA IDP link?
- VERIFIED flips state + deletes the (likely absent) verification row?
- Routes `POST /app/groups/{name}/verify/github` AND `GET /app/groups/{name}/verify/connect-github` registered?
- `web` `LoginHandler` accepts `idp_hint` and `return_to` query params, with `return_to` validated as a relative path (rejects absolute URLs and protocol-relative `//evil.com`)?
- App's `connectGitHub` handler redirects to `/oidc/login?idp_hint=<GitHub IDP UUID>&return_to=<verify-url>` — no manual PKCE/state, no manual cookie writes?
- verify.jte branches on `GroupValidator.kindOf` so GitHub groups get the GitHub UI, with both "Verify with GitHub" and "Connect GitHub" buttons?
- All seven commits (interface, impl, service, verify route, web LoginHandler, connect route, template) land cleanly?
- The `web` snapshot is published/installed before the app commits depend on it?

---

## What this plan deliberately does NOT do

- **Periodic re-verification.** If a user is removed from the GitHub org, the group stays VERIFIED. Design note says this is acceptable for v1.
- **End-to-end GitHub test.** Requires a real GitHub OAuth app + real GitHub account that's a member of an org — out of scope for automated tests.
- **Production-ready base URL.** The "Connect GitHub" handler hardcodes `http://localhost:8080` for the OIDC return + state URL. Replace with a config-driven `app.baseUrl` when promoting beyond local dev.
