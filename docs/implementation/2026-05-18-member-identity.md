# Member Identity from FusionAuth — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each group member's FusionAuth email and username on the members page instead of the raw user UUID.

**Architecture:** `User` becomes `(userId, email, username)`. `Member` carries a `User` instead of a bare UUID, but keeps a `(…, UUID, …)` convenience constructor and a `userId()` accessor so existing call sites are untouched. `MembershipService.listMembers` enriches members with one batched `FusionAuthClient.searchUsersByIds` call; templates render `member.user()`.

**Tech Stack:** Java 25 (records, JPMS), `latte` build tool, JTE 3.x templates, FusionAuth Java client, Cloudflare D1, TestNG integration tests (require local FusionAuth `:9011` + D1 network access).

**Spec:** `docs/design/2026-05-18-member-identity-design.md`

**Deviation from spec (approved with user):** Instead of editing every `new Member(...)` / `member.userId()` site, `Member` gets a UUID convenience constructor and a `userId()` accessor. End state matches the spec; churn is confined to the paths that actually have identity.

---

## Pre-flight

- [ ] **Confirm environment**

Run: `cd /Users/bpontarelli/dev/latte-java/app && ls src/main/fusionauth && curl -s -o /dev/null -w "%{http_code}" http://localhost:9011`
Expected: directory listing, then `200` (FusionAuth up). If not `200`, start it: `cd src/main/fusionauth && docker compose --profile mailcatcher up -d` and wait until `curl` returns `200`.

- [ ] **Baseline green**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test`
Expected: BUILD SUCCESS, all tests pass. This is the safety net for the refactor tasks. If it fails before any change, stop and report.

---

## Task 1: `User` record + JWT mapping + viewer templates

**Files:**
- Modify: `src/main/java/org/lattejava/app/model/User.java`
- Modify: `src/main/java/org/lattejava/app/service/UserService.java`
- Modify: `web/layout/sidebar.jte:36`
- Modify: `web/pages/dashboard.jte:12`

- [ ] **Step 1: Rewrite `User.java`**

Replace the record body so the third component is `username`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

/**
 * The signed-in user, threaded into the main layout via the View shell.
 */
public record User(
    UUID userId,      // FusionAuth user UUID (sub claim)
    String email,     // primary identifier
    String username   // FusionAuth username; null only for not-yet-registered (invited) users
) {
}
```

- [ ] **Step 2: Rewrite `UserService.toUser(JWT)` and add the FA-user mapper**

Replace the entire body of `src/main/java/org/lattejava/app/service/UserService.java` with:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.jwt;

public class UserService {
  /**
   * Convert a JWT to a User. The `sub` claim carries the FusionAuth user UUID; `preferred_username`
   * carries the FusionAuth username.
   *
   * @param jwt The JWT to convert.
   * @return The User.
   */
  public static User toUser(JWT jwt) {
    String sub = jwt.getString("sub");
    if (sub == null) {
      throw new IllegalStateException("JWT missing required [sub] claim");
    }

    UUID userId = UUID.fromString(sub);
    String email = jwt.getString("email");
    String username = jwt.getString("preferred_username");
    return new User(userId, email, username);
  }

  /**
   * Convert a FusionAuth domain user to a User. Used to enrich members and invitees from FusionAuth
   * lookups.
   *
   * @param faUser The FusionAuth user.
   * @return The User.
   */
  public static User toUser(io.fusionauth.domain.User faUser) {
    return new User(faUser.id, faUser.email, faUser.username);
  }
}
```

> Implementation check (from spec): after Task 1 compiles, sign in locally and confirm the JWT carries the FA username under `preferred_username`. If the local token uses a different claim name, change the `jwt.getString("preferred_username")` argument here only — do not add fallbacks.

- [ ] **Step 3: Update `web/layout/sidebar.jte` line 36**

Find:

```jte
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${view.viewer().name()}</div>
```

Replace `view.viewer().name()` with `view.viewer().username()`:

```jte
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${view.viewer().username()}</div>
```

- [ ] **Step 4: Update `web/pages/dashboard.jte` line 12**

Find:

```jte
!{var title = view.viewer().name().split(" ")[0];}
```

Replace with (no space-split — username is a single token):

```jte
!{var title = view.viewer().username();}
```

- [ ] **Step 5: Build and run the existing suite**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test`
Expected: BUILD SUCCESS, all existing tests pass. (`new User(x, y, z)` sites still compile — the rename is positional. No Java caller used `User.name()`.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/model/User.java src/main/java/org/lattejava/app/service/UserService.java web/layout/sidebar.jte web/pages/dashboard.jte
git commit -m "User: replace name with FusionAuth username

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `Member` carries `User` (with UUID convenience constructor)

**Files:**
- Modify: `src/main/java/org/lattejava/app/model/Member.java`

- [ ] **Step 1: Rewrite `Member.java`**

Replace the record body with the canonical `User`-based form plus a UUID convenience constructor and a `userId()` accessor:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record Member(
    String groupName,
    User user,
    Role role,
    MembershipState state,
    UUID invitedBy,
    Instant invitedAt,
    Instant joinedAt
) {
  /**
   * Convenience constructor for callers that only have the user's UUID (the D1 read path and tests).
   * The email and username are left null until the member is enriched from FusionAuth.
   */
  public Member(String groupName, UUID userId, Role role, MembershipState state, UUID invitedBy,
                Instant invitedAt, Instant joinedAt) {
    this(groupName, new User(userId, null, null), role, state, invitedBy, invitedAt, joinedAt);
  }

  /**
   * @return The member's FusionAuth user UUID.
   */
  public UUID userId() {
    return user.userId();
  }
}
```

- [ ] **Step 2: Build and run the existing suite**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test`
Expected: BUILD SUCCESS, all tests pass. Every existing `new Member(groupName, uuid, …)` resolves to the convenience constructor; `DatabaseClient.insertMember`'s `member.userId()` and `rowToMember`'s UUID argument are unchanged.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/model/Member.java
git commit -m "Member: carry User, keep UUID convenience ctor + userId() accessor

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Enrich members from FusionAuth + identity-bearing invites

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Test: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java` (it follows the existing pattern in that file: insert a fixture against `BaseTest.db`, then exercise the service). Place it alphabetically among the existing `@Test` methods:

```java
  @Test
  public void listMembersEnrichesUserFromFusionAuth() {
    FusionAuthClient fa = new FusionAuthClient(main.config.get("fusionauth.apiKey"), main.config.get("fusionauth.baseUrl"));
    UUID testUserId = fa.retrieveUserByEmail("test@lattejava.org").successResponse.user.id;

    db.query("DELETE FROM members WHERE group_name = ?", "test.enrich.fixture");
    db.query("DELETE FROM groups WHERE name = ?", "test.enrich.fixture");
    db.query("INSERT INTO groups (name, description, state, verification_code, created_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
        "test.enrich.fixture", "Enrich fixture", "VERIFIED", null, 1L, 1L);
    db.insertMember(new Member("test.enrich.fixture", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));

    List<Member> members = service.listMembers("test.enrich.fixture");

    assertEquals(members.size(), 1);
    assertEquals(members.getFirst().user().userId(), testUserId);
    assertEquals(members.getFirst().user().email(), "test@lattejava.org");
  }
```

If `service` / `main` / `db` are not already in scope in this class, mirror exactly how the other `@Test` methods in this file obtain them (do not invent new wiring — copy the existing pattern in `MembershipServiceTest`).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL on `listMembersEnrichesUserFromFusionAuth` — `members.getFirst().user().email()` is `null` (the D1 read path builds an id-only `User`).

- [ ] **Step 3: Enrich in `MembershipService.listMembers`**

Replace the existing method in `src/main/java/org/lattejava/app/service/MembershipService.java`:

```java
  public List<Member> listMembers(String groupName) {
    return databaseClient.listMembers(groupName);
  }
```

with:

```java
  public List<Member> listMembers(String groupName) {
    List<Member> members = databaseClient.listMembers(groupName);
    if (members.isEmpty()) {
      return members;
    }

    List<UUID> ids = members.stream().map(Member::userId).toList();
    ClientResponse<SearchResponse, ?> response = fusionAuth.searchUsersByIds(ids);
    Map<UUID, io.fusionauth.domain.User> byId = new HashMap<>();
    if (response.wasSuccessful() && response.successResponse != null && response.successResponse.users != null) {
      for (io.fusionauth.domain.User faUser : response.successResponse.users) {
        byId.put(faUser.id, faUser);
      }
    }

    List<Member> enriched = new ArrayList<>(members.size());
    for (Member m : members) {
      io.fusionauth.domain.User faUser = byId.get(m.userId());
      User user = faUser == null ? m.user() : UserService.toUser(faUser);
      enriched.add(new Member(m.groupName(), user, m.role(), m.state(), m.invitedBy(), m.invitedAt(), m.joinedAt()));
    }
    return enriched;
  }
```

If `SearchResponse` is not resolvable, it is `io.fusionauth.domain.api.user.SearchResponse` (the return type of `FusionAuthClient.searchUsersByIds`); add the matching import in the style already used by this file (the file uses `import module fusionauth.java.client;`, so it should resolve without a new import — only add one if compilation fails).

- [ ] **Step 4: Make `invite` build an identity-bearing `Member`**

In `src/main/java/org/lattejava/app/service/MembershipService.java`, the `invite` method currently creates the member as:

```java
    Instant now = Instant.now();
    Member member = new Member(
        request.groupName(),
        userId,
        request.role(),
        MembershipState.PENDING,
        inviter.userId(),
        now,
        null
    );
    databaseClient.insertMember(member);
    return member;
```

Two changes:

1. In the existing-FA-user branch (where `lookup.wasSuccessful() && lookup.successResponse.user != null`), capture the FA user. That branch already references `lookup.successResponse.user`; add right after `userId = lookup.successResponse.user.id;`:

```java
      User invitedUser = UserService.toUser(lookup.successResponse.user);
```

   In the newly-created-user branch (the `else` that calls `fusionAuth.register`), after a successful register add:

```java
      User invitedUser = new User(userId, email, null);
```

   Declare `User invitedUser;` before the `if`/`else` so both branches assign it (mirror how `userId` is already declared before the branch).

2. Replace the member construction to use `invitedUser`:

```java
    Instant now = Instant.now();
    Member member = new Member(
        request.groupName(),
        invitedUser,
        request.role(),
        MembershipState.PENDING,
        inviter.userId(),
        now,
        null
    );
    databaseClient.insertMember(member);
    return member;
```

`databaseClient.insertMember` reads `member.userId()` (the accessor added in Task 2), so the D1 write is unaffected.

- [ ] **Step 5: Run the test, verify it passes**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: PASS, including `listMembersEnrichesUserFromFusionAuth` and all pre-existing methods in the class.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "MembershipService: enrich members from FusionAuth, identity-bearing invites

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Render email + username in `member-row.jte`

**Files:**
- Modify: `web/components/member-row.jte`

- [ ] **Step 1: Replace the avatar key (line ~21)**

Find:

```jte
    @template.components.avatar(email = member.userId().toString(), size = 32)
```

Replace with (fixes the bug — avatar was keyed by the UUID):

```jte
    @template.components.avatar(email = member.user().email(), size = 32)
```

- [ ] **Step 2: Replace the identity block (lines ~23-30)**

Find:

```jte
  <div class="min-w-0">
    <div class="flex items-center gap-2">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate font-mono">${member.userId().toString()}</div>
      @if(isPending)
        @template.components.badge(label = "invited", tone = "warn")
      @endif
    </div>
  </div>
```

Replace with (email is always primary; username is a muted secondary line only for non-pending members, which are guaranteed to have one):

```jte
  <div class="min-w-0">
    <div class="flex items-center gap-2">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">${member.user().email()}</div>
      @if(isPending)
        @template.components.badge(label = "invited", tone = "warn")
      @endif
    </div>
    @if(!isPending)
      <div class="text-xs text-slate-500 dark:text-slate-400 truncate">${member.user().username()}</div>
    @endif
  </div>
```

- [ ] **Step 3: Form action URLs are already correct**

Lines ~39, ~48, ~53 use `member.userId().toString()` in the form `action`. Leave them — `member.userId()` is the Task 2 accessor and still returns the UUID. No change.

- [ ] **Step 4: Build and run the existing suite**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test`
Expected: BUILD SUCCESS, all tests pass (JTE compiles; `MainTest.groupMembers` still asserts the page contains `org.lattejava`).

- [ ] **Step 5: Commit**

```bash
git add web/components/member-row.jte
git commit -m "member-row: render email + username instead of UUID

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Rendering assertion + full verification

**Files:**
- Modify: `src/test/java/org/lattejava/app/tests/MainTest.java` (the existing `groupMembers` test)

- [ ] **Step 1: Strengthen the `groupMembers` test**

In `src/test/java/org/lattejava/app/tests/MainTest.java`, find:

```java
  @Test
  public void groupMembers() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
  }
```

Replace the `assertBodyAs` line so it also asserts the seeded member's email is rendered (the `org.lattejava` group has one OWNER member: the FA test user, seeded by `BaseTest.resetAndSeedDatabase`):

```java
  @Test
  public void groupMembers() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava").contains("test@lattejava.org"));
  }
```

(The assertion targets `email`, which is guaranteed set for the kickstart user. Username is not asserted — a kickstart-provisioned user may not have one, and that is the spec's "registered users always have a username" assumption to confirm via the Task 1 implementation check, not a test invariant.)

- [ ] **Step 2: Run the test, verify the new assertion is exercised**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.MainTest`
Expected: PASS. `groupMembers` now confirms the rendered HTML contains `test@lattejava.org`. (If you want to see the red state first, temporarily `git stash` Task 4's `member-row.jte` change, rerun — it FAILs because the page shows the UUID, not the email — then `git stash pop`.)

- [ ] **Step 3: Full suite + boot smoke check**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test`
Expected: BUILD SUCCESS, entire suite green.

Then perform the Task 1 implementation check: `latte run`, sign in at `http://localhost:8080`, open a group's members page, confirm the member row shows the email (and the username line for registered members), and confirm the signed-in user's name in the sidebar shows the username. Stop the server when done.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "Test: assert members page renders member email

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-review notes (for the implementer)

- **Spec coverage:** `User` rename (Task 1) · `UserService` both mappers (Task 1) · `Member` with `User` (Task 2) · `DatabaseClient` unchanged via convenience ctor + `userId()` (Task 2) · `listMembers` batched enrichment (Task 3) · identity-bearing `invite` (Task 3) · `member-row.jte` email/username/avatar (Task 4) · `sidebar.jte`/`dashboard.jte` (Task 1) · rendering test (Task 5). All spec sections covered.
- **No route or D1 migration changes** — none in this plan.
- **Type consistency:** `UserService.toUser(io.fusionauth.domain.User)` returns `User`; used in `MembershipService.listMembers` and `invite`. `Member.userId()` is the accessor used by `DatabaseClient.insertMember` and `member-row.jte` form actions. `searchUsersByIds` returns `ClientResponse<SearchResponse, ?>` with `successResponse.users`.
- **Known assumption:** `preferred_username` claim name — verified by the Task 1 implementation check, not guarded in code (per spec decision).
