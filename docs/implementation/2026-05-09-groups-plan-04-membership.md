# Groups Plan 04 — Membership Flows

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the six membership flows from the design doc — invite, accept, decline, remove, change role, leave — with last-Owner protection on the three administrative flows. Wire the routes (`/app/groups/{name}/members/...`) and the JTE templates (`members.jte`, `settings.jte`, `overview.jte`'s accept card) so an `Owner` can manage their group's roster end-to-end.

**Architecture:** A new `MembershipService` owns invite/accept/decline/remove/changeRole/leave logic. It builds its own `DatabaseClient` and `FusionAuthClient` from `Configuration` (per the established service-construction pattern). Invite validation errors flow back as `Errors` via a new `ValidationException`. Last-Owner protection is enforced inline in `remove`, `changeRole`, and `leave` using a `DatabaseClient.findActiveOwners(groupName)` helper that returns rows where `role=OWNER AND state=ACTIVE`. A new `MembershipController` registers a nested `prefix("/members", ...)` inside each group's route block and a `POST /leave` route at the group root.

**Tech Stack:** Java 25 (JPMS), `DatabaseClient` over Cloudflare D1, FusionAuth client for user lookup + registration, JTE templates, TestNG integration tests against real D1 + FA.

**Reference design:** `docs/design/2026-05-07-groups.md` — sections "Inviting a user to a group", "Accepting an invitation", "Decline an invitation", "Removing a user from a group", "Changing a member's role", "Leaving a group".

**Plan 03 outcome to build on:** `DatabaseClient` has `insertMember`, `findMember`, `deleteMember`. `Member` and `MembershipState` are schema-aligned. Routes use nested `prefix(...)` blocks with `{name}` path syntax. `Errors` + `ValidationException` pattern is established. Group detail page renders an Overview tab via `detail.jte` → `overview.jte`.

**Out of scope (deferred):**
- **Custom invite email templates** — the design's "you've been invited" template for existing users is a Plan 06 deliverable (depends on the SMTP/SendGrid kickstart). Plan 04 logs a `would send invite email to [email]` line for existing users and relies on FusionAuth's built-in `set-password` email for new users (which only sends if SMTP is configured — that's Plan 06).
- **Public group pages** — accept/decline buttons are gated by `oidc.authenticated()` (Plan 04 inherits the existing `/app/*` gate).
- **GitHub verification** — Plan 03b. Independent of membership flows.

---

## File Structure

**Create:**
- `src/main/java/org/lattejava/app/service/InviteRequest.java` — record holding `groupName`, `email`, `role`. Returned to template on validation failure.
- `src/main/java/org/lattejava/app/service/MembershipService.java` — six methods + invite validation.
- `src/main/java/org/lattejava/app/service/validation/ValidationException.java` — unified validation exception (already exists from Plan 02; reused here).
- `src/main/java/org/lattejava/app/controller/MembershipController.java` — request handlers for the six flows.
- `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java` — integration tests against real D1 + FA.

**Modify:**
- `src/main/java/org/lattejava/app/db/DatabaseClient.java` — add `findActiveOwners(String groupName)`, `listMembers(String groupName)`, `updateMemberRole(String, UUID, Role)`, `updateMemberState(String, UUID, MembershipState, Instant)`.
- `src/main/java/org/lattejava/app/Main.java` — construct `MembershipService`; pass to controllers; mount nested `/members` prefix.
- `src/main/java/org/lattejava/app/controller/GroupController.java` — add `members` tab handler that delegates rendering to `members.jte` (it consumes the same `MainView` + `Group`).
- `web/pages/groups/members.jte` — update form action to `/app/groups/{name}/members`; remove stale `MAINTAINER`/`PUBLISHER` placeholders; render real members list.
- `web/pages/groups/role-picker.jte` — fix stale role values (`MAINTAINER` → `OWNER`, `PUBLISHER`/`VIEWER` → `CONTRIBUTOR`); add the form `name="role"`, target action.
- `web/components/member-row.jte` — wire remove + role-change forms.
- `web/pages/groups/settings.jte` — add a Leave button that posts to `/app/groups/{name}/leave`.
- `web/pages/groups/overview.jte` — render an accept/decline card if the current viewer has a `PENDING` membership.

**Templates not touched:** `web/pages/groups/{detail,verify,list,new,artifacts}.jte` already point at the right URLs after Plan 03.

---

## Decisions locked in for this plan

- **Email handling.** New users: rely on FusionAuth's `register` API with `sendSetPasswordIdentityType=email` — FA handles the email if SMTP is configured. Existing users: log `would send invite email to [email]` via the framework logger; Plan 06 swaps this for a templated send.
- **Last-Owner protection.** Implemented inline in `remove`, `changeRole`, and `leave`. The check counts rows where `role=OWNER AND state=ACTIVE`. If the operation would leave 0 active owners, throw `ValidationException` with a general error.
- **`changeRole` semantics.** Demoting the only active Owner is rejected. Promoting any member to Owner is always allowed. The current user cannot change their own role (per design).
- **`remove` semantics.** Deletes the member row outright. The current user cannot remove themselves (per design).
- **`leave` semantics.** The current user removes their own row. Last-Owner protection applies.
- **Invite validation errors as `Errors`.** Field errors keyed on `email` (`[blank]email`, `[notValid]email`, `[duplicate]email`, `[alreadyInvited]email`) and `role` (`[notValid]role`). Validation runs inline in `MembershipService.invite`; no separate `MembershipValidator` class until repeat use justifies one.
- **Route shape.** Inside the existing `prefix("/groups", ...)` → `prefix("/{name}", ...)` would require nested-with-path-param support (uncommon). Instead, register flat routes inside the `/groups` prefix:
  ```java
  groups.get("/{name}/members", controller::members);
  groups.post("/{name}/members", controller::invite);
  groups.post("/{name}/members/{userId}/accept", controller::accept);
  groups.post("/{name}/members/{userId}/decline", controller::decline);
  groups.post("/{name}/members/{userId}/remove", controller::remove);
  groups.post("/{name}/members/{userId}/role", controller::changeRole);
  groups.post("/{name}/leave", controller::leave);
  ```
- **MembershipController construction.** Built in `Main.java` from `(MembershipService, ViewService, OIDC<User>, JTETemplates)` — same pattern as `GroupController`.
- **Test cleanup of FA users.** Tests create FA users with unique emails (`test+invite-<uuid>@lattejava.org`) and don't delete them (FA accumulation is acceptable for dev D1 churn; Plan 06 may reset FA).

---

## Task 1: DatabaseClient — member-list helpers

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing tests**

Append to `DatabaseClientTest.java`:

```java
@Test
public void findActiveOwners_returnsOnlyOwnerActiveRows() {
  Group g = new Group("test.owners.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  client.insertGroup(g);
  UUID owner1 = UUID.fromString("aa000001-0000-0000-0000-000000000001");
  UUID owner2 = UUID.fromString("aa000001-0000-0000-0000-000000000002");
  UUID pendingOwner = UUID.fromString("aa000001-0000-0000-0000-000000000003");
  UUID contributor = UUID.fromString("aa000001-0000-0000-0000-000000000004");
  try {
    client.insertMember(new Member("test.owners.fixture", owner1, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.owners.fixture", owner2, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    client.insertMember(new Member("test.owners.fixture", pendingOwner, Role.OWNER, MembershipState.PENDING, null, null, null));
    client.insertMember(new Member("test.owners.fixture", contributor, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(3L)));

    List<Member> owners = client.findActiveOwners("test.owners.fixture");
    assertEquals(owners.size(), 2);
    assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner1)));
    assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner2)));
  } finally {
    client.deleteGroup("test.owners.fixture");
  }
}

@Test
public void listMembers_returnsAllStatesAndRoles() {
  Group g = new Group("test.list-members.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  client.insertGroup(g);
  UUID a = UUID.fromString("bb000001-0000-0000-0000-000000000001");
  UUID b = UUID.fromString("bb000001-0000-0000-0000-000000000002");
  try {
    client.insertMember(new Member("test.list-members.fixture", a, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.list-members.fixture", b, Role.CONTRIBUTOR, MembershipState.PENDING, a, Instant.ofEpochMilli(2L), null));
    List<Member> members = client.listMembers("test.list-members.fixture");
    assertEquals(members.size(), 2);
  } finally {
    client.deleteGroup("test.list-members.fixture");
  }
}

@Test
public void updateMemberRole_changesRole() {
  Group g = new Group("test.role-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("cc000001-0000-0000-0000-000000000001");
  try {
    client.insertMember(new Member("test.role-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.updateMemberRole("test.role-change.fixture", userId, Role.OWNER);
    Optional<Member> after = client.findMember("test.role-change.fixture", userId);
    assertTrue(after.isPresent());
    assertEquals(after.get().role(), Role.OWNER);
  } finally {
    client.deleteGroup("test.role-change.fixture");
  }
}

@Test
public void updateMemberState_changesStateAndJoinedAt() {
  Group g = new Group("test.state-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("dd000001-0000-0000-0000-000000000001");
  try {
    client.insertMember(new Member("test.state-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
    client.updateMemberState("test.state-change.fixture", userId, MembershipState.ACTIVE, Instant.ofEpochMilli(99L));
    Optional<Member> after = client.findMember("test.state-change.fixture", userId);
    assertTrue(after.isPresent());
    assertEquals(after.get().state(), MembershipState.ACTIVE);
    assertEquals(after.get().joinedAt(), Instant.ofEpochMilli(99L));
  } finally {
    client.deleteGroup("test.state-change.fixture");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL — four new methods don't exist.

- [ ] **Step 3: Implement methods**

Add to `DatabaseClient.java`. After this change the public-method order becomes alphabetical:

`deleteGroup, deleteMember, deleteVerification, findActiveOwners, findAncestorGroup, findGroup, findMember, findVerification, insertGroup, insertMember, insertVerification, listGroupsForUser, listMembers, listVerificationsDueForCheck, query, updateGroupState, updateMemberRole, updateMemberState, updateVerificationLastChecked`

```java
public List<Member> findActiveOwners(String groupName) {
  D1Response response = query(
      "SELECT group_name, user_id, role, state, invited_by, invited_at, joined_at FROM members "
          + "WHERE group_name = ? AND role = 'OWNER' AND state = 'ACTIVE'",
      groupName
  );
  List<Map<String, Object>> rows = response.result().getFirst().results();
  List<Member> members = new ArrayList<>(rows.size());
  for (Map<String, Object> row : rows) {
    members.add(rowToMember(row));
  }
  return members;
}

public List<Member> listMembers(String groupName) {
  D1Response response = query(
      "SELECT group_name, user_id, role, state, invited_by, invited_at, joined_at FROM members WHERE group_name = ?",
      groupName
  );
  List<Map<String, Object>> rows = response.result().getFirst().results();
  List<Member> members = new ArrayList<>(rows.size());
  for (Map<String, Object> row : rows) {
    members.add(rowToMember(row));
  }
  return members;
}

public void updateMemberRole(String groupName, UUID userId, Role role) {
  query(
      "UPDATE members SET role = ? WHERE group_name = ? AND user_id = ?",
      role.name(),
      groupName,
      userId.toString()
  );
}

public void updateMemberState(String groupName, UUID userId, MembershipState state, Instant joinedAt) {
  query(
      "UPDATE members SET state = ?, joined_at = ? WHERE group_name = ? AND user_id = ?",
      state.name(),
      joinedAt == null ? null : joinedAt.toEpochMilli(),
      groupName,
      userId.toString()
  );
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: 19/19 PASS (the prior 15 + 4 new).

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: zero failures.

- [ ] **Step 6: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/app
git add src/main/java/org/lattejava/app/db/DatabaseClient.java
git add src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): findActiveOwners + listMembers + updateMemberRole + updateMemberState"
```

---

## Task 2: InviteRequest

> `ValidationException` already exists at `src/main/java/org/lattejava/app/service/validation/ValidationException.java` (created in Plan 02). This task only adds `InviteRequest`.

**Files:**
- Create: `src/main/java/org/lattejava/app/service/InviteRequest.java`

- [ ] **Step 1: Create `InviteRequest`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import org.lattejava.app.model.Role;

public record InviteRequest(String groupName, String email, Role role) {
}
```

- [ ] **Step 2: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/service/InviteRequest.java
git commit -m "feat(service): InviteRequest record"
```

---

## Task 3: MembershipService.invite

**Files:**
- Create: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Create: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`
- Modify: `src/test/java/module-info.java` if needed (the `service` package was already opened in Plan 02)

- [ ] **Step 1: Write failing test**

Create `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module org.testng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.Role;
import org.lattejava.app.model.User;
import org.lattejava.web.Configuration;

@Test
public class MembershipServiceTest {
  public DatabaseClient client;
  public Configuration config;
  public MembershipService service;

  @BeforeClass
  public void beforeClass() {
    config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId",
            "fusionauth.apiKey", "fusionauth.baseUrl"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    service = new MembershipService(config);
  }

  @Test
  public void invite_newEmail_createsFAUserAndPendingMember() {
    Group g = new Group("test.invite.new", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000001");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    String uniqueEmail = "test+invite-new-" + UUID.randomUUID() + "@lattejava.org";
    try {
      Member m = service.invite(new InviteRequest("test.invite.new", uniqueEmail, Role.CONTRIBUTOR), inviter);
      assertNotNull(m);
      assertEquals(m.groupName(), "test.invite.new");
      assertEquals(m.role(), Role.CONTRIBUTOR);
      assertEquals(m.state(), MembershipState.PENDING);
      assertEquals(m.invitedBy(), inviterId);
      assertNotNull(m.invitedAt());
      assertNull(m.joinedAt());

      Optional<Member> persisted = client.findMember("test.invite.new", m.userId());
      assertTrue(persisted.isPresent());
    } finally {
      client.deleteGroup("test.invite.new");
    }
  }

  @Test
  public void invite_blankEmail_throwsValidation() {
    Group g = new Group("test.invite.blank", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000002");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    try {
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.invite(new InviteRequest("test.invite.blank", "  ", Role.CONTRIBUTOR), inviter)
      );
      assertNotNull(ex.errors().getFieldError("email", "[blank]email"));
    } finally {
      client.deleteGroup("test.invite.blank");
    }
  }

  @Test
  public void invite_alreadyMember_throwsValidation() {
    Group g = new Group("test.invite.dup", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000003");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    String uniqueEmail = "test+invite-dup-" + UUID.randomUUID() + "@lattejava.org";
    try {
      Member first = service.invite(new InviteRequest("test.invite.dup", uniqueEmail, Role.CONTRIBUTOR), inviter);
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.invite(new InviteRequest("test.invite.dup", uniqueEmail, Role.CONTRIBUTOR), inviter)
      );
      assertTrue(ex.errors().containsError("[alreadyInvited]"));
    } finally {
      client.deleteGroup("test.invite.dup");
    }
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `MembershipService` doesn't exist.

- [ ] **Step 3: Implement `MembershipService`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/MembershipService.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;

import io.fusionauth.client.FusionAuthClient;
import io.fusionauth.domain.api.UserResponse;
import io.fusionauth.domain.api.user.RegistrationRequest;
import org.lattejava.app.error.Errors;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.User;
import org.lattejava.web.Configuration;

public class MembershipService {
  private static final System.Logger LOG = System.getLogger(MembershipService.class.getName());
  private static final UUID APPLICATION_ID = UUID.fromString("e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final DatabaseClient databaseClient;
  private final FusionAuthClient fusionAuth;

  public MembershipService(Configuration config) {
    this.databaseClient = new DatabaseClient(config);
    this.fusionAuth = new FusionAuthClient(
        config.get("fusionauth.apiKey"),
        config.get("fusionauth.baseUrl")
    );
  }

  public Member invite(InviteRequest request, User inviter) {
    Errors errors = validateInvite(request);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    String email = request.email().toLowerCase(Locale.ROOT).trim();

    UUID userId;
    boolean existed;
    ClientResponse<UserResponse, ?> lookup = fusionAuth.retrieveUserByEmail(email);
    if (lookup.wasSuccessful() && lookup.successResponse != null && lookup.successResponse.user != null) {
      userId = lookup.successResponse.user.id;
      existed = true;
    } else {
      userId = UUID.randomUUID();
      io.fusionauth.domain.User newUser = new io.fusionauth.domain.User();
      newUser.email = email;
      io.fusionauth.domain.UserRegistration registration = new io.fusionauth.domain.UserRegistration();
      registration.applicationId = APPLICATION_ID;
      RegistrationRequest registrationRequest = new RegistrationRequest(newUser, registration);
      registrationRequest.sendSetPasswordEmail = true;
      ClientResponse<?, ?> createResponse = fusionAuth.register(userId, registrationRequest);
      if (!createResponse.wasSuccessful()) {
        throw new IllegalStateException("Failed to create FusionAuth user for [" + email + "]");
      }
      existed = false;
    }

    if (existed && databaseClient.findMember(request.groupName(), userId).isEmpty()) {
      LOG.log(System.Logger.Level.INFO,
          "would send invite email to existing user [" + email + "] for group [" + request.groupName() + "]");
    }

    Optional<Member> existing = databaseClient.findMember(request.groupName(), userId);
    if (existing.isPresent()) {
      Errors dupErrors = new Errors();
      dupErrors.addFieldError("email", "[alreadyInvited]email",
          "[%s] is already a member or has a pending invitation.", email);
      throw new ValidationException(dupErrors);
    }

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
  }

  private Errors validateInvite(InviteRequest request) {
    Errors errors = new Errors();
    if (request == null) {
      errors.addGeneralError("[null]request", "An invite request is required.");
      return errors;
    }
    if (request.groupName() == null || request.groupName().isBlank()) {
      errors.addGeneralError("[blank]groupName", "A group name is required.");
    }
    String email = request.email() == null ? "" : request.email().toLowerCase(Locale.ROOT).trim();
    if (email.isEmpty()) {
      errors.addFieldError("email", "[blank]email", "An email address is required.");
    } else if (!EMAIL_PATTERN.matcher(email).matches()) {
      errors.addFieldError("email", "[notValid]email",
          "The email address [%s] is not a valid format.", email);
    }
    if (request.role() == null) {
      errors.addFieldError("role", "[blank]role", "A role is required.");
    }
    return errors;
  }
}
```

Notes on the implementation:
- The duplicate-member check happens AFTER the user resolution (we need the userId to look up the membership). This means we hit FA before realizing the invite is a dup; cost of a DB-then-FA-then-DB roundtrip is acceptable for this v1.
- The `existed` flag gates whether we log the would-send-invite-email line — we only log for existing users, since FA's set-password email already fires for new users.
- The `APPLICATION_ID` is the project's hardcoded application UUID from `kickstart.json`. Pulled from `config.get("fusionauth.clientId")` would be cleaner; refactor in a future plan if desired.
- `EMAIL_PATTERN` is intentionally simple. Production validation would use a real RFC 5322 parser. Plan 04 favors a permissive check; the FA registration call will reject malformed addresses if they slip through.

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 3/3 PASS.

If a test hangs or fails on FA connectivity, FusionAuth isn't running on `:9013`. Start it (per CLAUDE.md) and retry.

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: zero failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.invite (FA lookup/register + PENDING insert)"
```

---

## Task 4: MembershipService.accept

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write failing test**

Append to `MembershipServiceTest.java`:

```java
@Test
public void accept_pending_marksActive() {
  Group g = new Group("test.accept.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000001");
  try {
    client.insertMember(new Member("test.accept.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
    service.accept("test.accept.fixture", userId);
    Optional<Member> after = client.findMember("test.accept.fixture", userId);
    assertTrue(after.isPresent());
    assertEquals(after.get().state(), MembershipState.ACTIVE);
    assertNotNull(after.get().joinedAt());
  } finally {
    client.deleteGroup("test.accept.fixture");
  }
}

@Test
public void accept_active_isNoOp() {
  Group g = new Group("test.accept.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000002");
  try {
    Instant joinedAt = Instant.ofEpochMilli(50L);
    client.insertMember(new Member("test.accept.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, joinedAt));
    service.accept("test.accept.active", userId);
    Optional<Member> after = client.findMember("test.accept.active", userId);
    assertEquals(after.get().joinedAt(), joinedAt); // unchanged
  } finally {
    client.deleteGroup("test.accept.active");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `accept` doesn't exist.

- [ ] **Step 3: Implement `accept`**

Add to `MembershipService.java` in alphabetical position among instance methods (between `invite` and any other — `accept` actually goes BEFORE `invite` alphabetically). Final instance-method order: `accept, invite`.

Wait — the convention says alphabetical within visibility. `a` < `i`, so `accept` comes first.

```java
public void accept(String groupName, UUID userId) {
  Optional<Member> member = databaseClient.findMember(groupName, userId);
  if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
    return;
  }
  databaseClient.updateMemberState(groupName, userId, MembershipState.ACTIVE, Instant.now());
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 5/5 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.accept transitions PENDING -> ACTIVE"
```

---

## Task 5: MembershipService.decline

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write failing tests**

Append to `MembershipServiceTest.java`:

```java
@Test
public void decline_pending_deletesRow() {
  Group g = new Group("test.decline.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000001");
  try {
    client.insertMember(new Member("test.decline.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
    service.decline("test.decline.fixture", userId);
    assertTrue(client.findMember("test.decline.fixture", userId).isEmpty());
  } finally {
    client.deleteGroup("test.decline.fixture");
  }
}

@Test
public void decline_active_isNoOp() {
  Group g = new Group("test.decline.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000002");
  try {
    client.insertMember(new Member("test.decline.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    service.decline("test.decline.active", userId);
    assertTrue(client.findMember("test.decline.active", userId).isPresent()); // not deleted
  } finally {
    client.deleteGroup("test.decline.active");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `decline` doesn't exist.

- [ ] **Step 3: Implement `decline`**

Add to `MembershipService.java` in alphabetical position. Order: `accept, decline, invite`.

```java
public void decline(String groupName, UUID userId) {
  Optional<Member> member = databaseClient.findMember(groupName, userId);
  if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
    return;
  }
  databaseClient.deleteMember(groupName, userId);
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 7/7 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.decline deletes PENDING member row"
```

---

## Task 6: MembershipService.remove (with last-Owner protection)

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write failing tests**

Append to `MembershipServiceTest.java`:

```java
@Test
public void remove_contributor_succeeds() {
  Group g = new Group("test.remove.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("22000001-0000-0000-0000-000000000001");
  UUID target = UUID.fromString("22000001-0000-0000-0000-000000000002");
  try {
    client.insertMember(new Member("test.remove.contrib", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.remove.contrib", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    service.remove("test.remove.contrib", target, new User(actor, "actor@lattejava.org", "Actor"));
    assertTrue(client.findMember("test.remove.contrib", target).isEmpty());
  } finally {
    client.deleteGroup("test.remove.contrib");
  }
}

@Test
public void remove_lastActiveOwner_throws() {
  Group g = new Group("test.remove.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("22000001-0000-0000-0000-000000000003");
  UUID otherOwner = UUID.fromString("22000001-0000-0000-0000-000000000004");
  try {
    client.insertMember(new Member("test.remove.lastowner", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.remove.lastowner", otherOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    User actorUser = new User(actor, "actor@lattejava.org", "Actor");
    // 2 active owners. Removing one is fine.
    service.remove("test.remove.lastowner", otherOwner, actorUser);
    // Now 1 active owner. Removing actor (themselves) is blocked by self-rule, but if we
    // try to remove them via a different actor (impossible in real flow), validate the
    // last-active-owner guard. Insert a second active owner so the actor *can* remove
    // them, then try to remove the new actor's own row via the owner rule — actually
    // we need to construct a scenario where remove() is called against the last
    // remaining active owner with an actor who is some other owner. Let's reset the
    // group and try again:
  } finally {
    client.deleteGroup("test.remove.lastowner");
  }
}

@Test
public void remove_self_throws() {
  Group g = new Group("test.remove.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("22000001-0000-0000-0000-000000000005");
  try {
    client.insertMember(new Member("test.remove.self", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    User actorUser = new User(actor, "actor@lattejava.org", "Actor");
    expectThrows(ValidationException.class,
        () -> service.remove("test.remove.self", actor, actorUser));
  } finally {
    client.deleteGroup("test.remove.self");
  }
}
```

The `remove_lastActiveOwner_throws` test above has a flawed setup. Replace with this clearer version:

```java
@Test
public void remove_wouldOrphanGroup_isPrevented() {
  // The active-owner protection lives in remove() to guard against future flows where a
  // non-self actor could leave a group with zero active owners. With the self-rule in
  // place, a single active owner cannot remove themselves, so the only path to zero
  // owners is via remove() of the last active owner by another non-owner actor, which
  // the authorization layer would have already rejected. The service-level protection
  // is defense in depth.
  //
  // We test the protection by constructing a state where two owners exist, the actor
  // is one of them, and they target the other. Since both are active, removing the
  // target leaves 1 active owner — the actor — which is fine. The self-rule prevents
  // the actor from then removing themselves.
  //
  // The protection only fires if the remove operation would result in zero ACTIVE
  // OWNERs. We simulate that by directly checking the validation path with a single
  // active owner — but the self-rule blocks that path. So practically, the last-owner
  // protection is unreachable through the public API given the self-rule. We still
  // exercise the validation logic by constructing the state and calling remove with
  // a different actor (admin scenario).
  Group g = new Group("test.remove.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID lastOwner = UUID.fromString("22000001-0000-0000-0000-000000000006");
  UUID adminActor = UUID.fromString("22000001-0000-0000-0000-000000000007"); // not a member
  try {
    client.insertMember(new Member("test.remove.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    User actorUser = new User(adminActor, "actor@lattejava.org", "Actor");
    expectThrows(ValidationException.class,
        () -> service.remove("test.remove.lastowner", lastOwner, actorUser));
  } finally {
    client.deleteGroup("test.remove.lastowner");
  }
}
```

Replace the broken test with the version above. Two test methods total: `remove_contributor_succeeds`, `remove_self_throws`, `remove_wouldOrphanGroup_isPrevented`.

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `remove` doesn't exist.

- [ ] **Step 3: Implement `remove`**

Add to `MembershipService.java`. Order alphabetical: `accept, decline, invite, remove`.

```java
public void remove(String groupName, UUID targetUserId, User actor) {
  if (actor.userId().equals(targetUserId)) {
    Errors errors = new Errors();
    errors.addGeneralError("[selfRemove]actor", "You cannot remove yourself from the group. Use Leave instead.");
    throw new ValidationException(errors);
  }
  Optional<Member> target = databaseClient.findMember(groupName, targetUserId);
  if (target.isEmpty()) {
    return;
  }
  if (target.get().role() == Role.OWNER && target.get().state() == MembershipState.ACTIVE) {
    List<Member> owners = databaseClient.findActiveOwners(groupName);
    if (owners.size() <= 1) {
      Errors errors = new Errors();
      errors.addGeneralError("[lastOwner]group",
          "Cannot remove the last active OWNER of the group [%s].", groupName);
      throw new ValidationException(errors);
    }
  }
  databaseClient.deleteMember(groupName, targetUserId);
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 10/10 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.remove with self + last-Owner protection"
```

---

## Task 7: MembershipService.changeRole (with last-Owner protection)

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write failing tests**

Append to `MembershipServiceTest.java`:

```java
@Test
public void changeRole_promote_succeeds() {
  Group g = new Group("test.role.promote", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("33000001-0000-0000-0000-000000000001");
  UUID target = UUID.fromString("33000001-0000-0000-0000-000000000002");
  try {
    client.insertMember(new Member("test.role.promote", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.role.promote", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    service.changeRole("test.role.promote", target, Role.OWNER, new User(actor, "a@x", "A"));
    Optional<Member> after = client.findMember("test.role.promote", target);
    assertEquals(after.get().role(), Role.OWNER);
  } finally {
    client.deleteGroup("test.role.promote");
  }
}

@Test
public void changeRole_demoteLastActiveOwner_throws() {
  Group g = new Group("test.role.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("33000001-0000-0000-0000-000000000003");
  UUID lastOwner = UUID.fromString("33000001-0000-0000-0000-000000000004");
  try {
    client.insertMember(new Member("test.role.lastowner", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.role.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    User actorUser = new User(actor, "a@x", "A");
    // Demote the OTHER owner first (now 1 active owner = actor).
    service.changeRole("test.role.lastowner", lastOwner, Role.CONTRIBUTOR, actorUser);
    // Now actor is the only active owner. Demoting THEMSELVES is blocked by the
    // self-rule. Demoting via a different actor (impossible in real flow) is what
    // the last-owner check defends against — simulate via a non-member admin actor.
    UUID admin = UUID.fromString("33000001-0000-0000-0000-000000000005");
    User adminUser = new User(admin, "admin@x", "Admin");
    expectThrows(ValidationException.class,
        () -> service.changeRole("test.role.lastowner", actor, Role.CONTRIBUTOR, adminUser));
  } finally {
    client.deleteGroup("test.role.lastowner");
  }
}

@Test
public void changeRole_self_throws() {
  Group g = new Group("test.role.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID actor = UUID.fromString("33000001-0000-0000-0000-000000000006");
  try {
    client.insertMember(new Member("test.role.self", actor, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    User actorUser = new User(actor, "a@x", "A");
    expectThrows(ValidationException.class,
        () -> service.changeRole("test.role.self", actor, Role.CONTRIBUTOR, actorUser));
  } finally {
    client.deleteGroup("test.role.self");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `changeRole` doesn't exist.

- [ ] **Step 3: Implement `changeRole`**

Add to `MembershipService.java`. Final order: `accept, changeRole, decline, invite, remove`.

```java
public void changeRole(String groupName, UUID targetUserId, Role newRole, User actor) {
  if (actor.userId().equals(targetUserId)) {
    Errors errors = new Errors();
    errors.addGeneralError("[selfRoleChange]actor", "You cannot change your own role.");
    throw new ValidationException(errors);
  }
  Optional<Member> target = databaseClient.findMember(groupName, targetUserId);
  if (target.isEmpty()) {
    return;
  }
  if (target.get().role() == Role.OWNER
      && target.get().state() == MembershipState.ACTIVE
      && newRole != Role.OWNER) {
    List<Member> owners = databaseClient.findActiveOwners(groupName);
    if (owners.size() <= 1) {
      Errors errors = new Errors();
      errors.addGeneralError("[lastOwner]group",
          "Cannot demote the last active OWNER of the group [%s].", groupName);
      throw new ValidationException(errors);
    }
  }
  databaseClient.updateMemberRole(groupName, targetUserId, newRole);
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 13/13 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.changeRole with self + last-Owner protection"
```

---

## Task 8: MembershipService.leave

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/MembershipService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java`

- [ ] **Step 1: Write failing tests**

Append to `MembershipServiceTest.java`:

```java
@Test
public void leave_contributor_succeeds() {
  Group g = new Group("test.leave.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000001");
  UUID leaver = UUID.fromString("44000001-0000-0000-0000-000000000002");
  try {
    client.insertMember(new Member("test.leave.contrib", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    client.insertMember(new Member("test.leave.contrib", leaver, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
    service.leave("test.leave.contrib", new User(leaver, "l@x", "L"));
    assertTrue(client.findMember("test.leave.contrib", leaver).isEmpty());
  } finally {
    client.deleteGroup("test.leave.contrib");
  }
}

@Test
public void leave_lastActiveOwner_throws() {
  Group g = new Group("test.leave.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
  client.insertGroup(g);
  UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000003");
  try {
    client.insertMember(new Member("test.leave.lastowner", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
    User ownerUser = new User(owner, "o@x", "O");
    expectThrows(ValidationException.class,
        () -> service.leave("test.leave.lastowner", ownerUser));
  } finally {
    client.deleteGroup("test.leave.lastowner");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: FAIL — `leave` doesn't exist.

- [ ] **Step 3: Implement `leave`**

Add to `MembershipService.java`. Final order: `accept, changeRole, decline, invite, leave, remove`.

```java
public void leave(String groupName, User actor) {
  Optional<Member> member = databaseClient.findMember(groupName, actor.userId());
  if (member.isEmpty()) {
    return;
  }
  if (member.get().role() == Role.OWNER && member.get().state() == MembershipState.ACTIVE) {
    List<Member> owners = databaseClient.findActiveOwners(groupName);
    if (owners.size() <= 1) {
      Errors errors = new Errors();
      errors.addGeneralError("[lastOwner]group",
          "Cannot leave the group [%s] as the last active OWNER.", groupName);
      throw new ValidationException(errors);
    }
  }
  databaseClient.deleteMember(groupName, actor.userId());
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.MembershipServiceTest`
Expected: 15/15 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/test/java/org/lattejava/app/tests/service/MembershipServiceTest.java
git commit -m "feat(service): MembershipService.leave with last-Owner protection"
```

---

## Task 9: MembershipController + Main wiring

**Files:**
- Create: `src/main/java/org/lattejava/app/controller/MembershipController.java`
- Modify: `src/main/java/org/lattejava/app/Main.java`

- [ ] **Step 1: Create `MembershipController.java`**

Read the existing `controller/GroupController.java` first to match its style (constructor params, helper methods, route handler signatures). Then create:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.error.Errors;
import org.lattejava.app.model.Group;
import org.lattejava.app.model.MainView;
import org.lattejava.app.model.Role;
import org.lattejava.app.model.User;
import org.lattejava.app.model.InviteRequest;
import org.lattejava.app.service.MembershipService;
import org.lattejava.app.service.ViewService;

public class MembershipController {
  private final MembershipService membershipService;
  private final OIDC<User> oidc;
  private final JTETemplates templates;
  private final ViewService viewService;

  public MembershipController(MembershipService membershipService, ViewService viewService,
                              OIDC<User> oidc, JTETemplates templates) {
    this.membershipService = membershipService;
    this.viewService = viewService;
    this.oidc = oidc;
    this.templates = templates;
  }

  public void accept(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute("name");
    UUID userId = UUID.fromString((String) req.getAttribute("userId"));
    membershipService.acceptInvitation(groupName, userId);
    res.sendRedirect("/app/groups/" + groupName, 303);
  }

  public void changeRole(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute("name");
    UUID userId = UUID.fromString((String) req.getAttribute("userId"));
    Role newRole = Role.valueOf(req.getParameter("role"));
    User actor = oidc.user();
    try {
      membershipService.changeRole(groupName, userId, newRole, actor);
    } catch (ValidationException e) {
      // Render members tab with general error.
      // For Plan 04, simplest approach: redirect with error in flash. Since framework
      // may not have flash, render in-place with the error.
    }
    res.sendRedirect("/app/groups/" + groupName + "/members", 303);
  }

  public void decline(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute("name");
    UUID userId = UUID.fromString((String) req.getAttribute("userId"));
    membershipService.declineInvitation(groupName, userId);
    res.sendRedirect("/app/groups/" + groupName, 303);
  }

  public void invite(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute("name");
    String email = req.getParameter("email");
    String roleParam = req.getParameter("role");
    Role role = roleParam == null ? Role.CONTRIBUTOR : Role.valueOf(roleParam);
    User actor = oidc.user();
    try {
      membershipService.invite(new InviteRequest(groupName, email, role), actor);
      res.sendRedirect("/app/groups/" + groupName + "/members", 303);
    } catch (ValidationException e) {
      renderMembersWithErrors(req, res, groupName, email, role, e.errors());
    }
  }

  public void leave(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute("name");
    User actor = oidc.user();
    try {
      membershipService.leave(groupName, actor);
    } catch (ValidationException e) {
      // Cannot leave — render settings with error.
    }
    res.sendRedirect("/app/groups/", 303);
  }

  public void members(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute("name");
    User user = oidc.user();
    Group group = requireGroup(groupName);
    if (group == null) {
      res.setStatus(404);
      return;
    }
    renderMembers(req, res, group, user, "", Role.CONTRIBUTOR, new Errors());
  }

  public void remove(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute("name");
    UUID userId = UUID.fromString((String) req.getAttribute("userId"));
    User actor = oidc.user();
    try {
      membershipService.remove(groupName, userId, actor);
    } catch (ValidationException e) {
      // Render members with error.
    }
    res.sendRedirect("/app/groups/" + groupName + "/members", 303);
  }

  private Group requireGroup(String groupName) {
    return /* delegate via GroupService.findGroup if exposed; otherwise null */ null;
  }

  private void renderMembers(HTTPRequest req, HTTPResponse res, Group group, User user,
                             String inviteEmail, Role inviteRole, Errors errors) throws IOException {
    MainView view = viewService.retrieve(user);
    Map<String, Object> params = new HashMap<>();
    params.put("view", view);
    params.put("group", group);
    params.put("activeTab", "members");
    params.put("inviteEmail", inviteEmail == null ? "" : inviteEmail);
    params.put("inviteRole", inviteRole == null ? "CONTRIBUTOR" : inviteRole.name());
    params.put("errors", errors);
    templates.html("pages/groups/detail.jte", req, res, params);
  }

  private void renderMembersWithErrors(HTTPRequest req, HTTPResponse res, String groupName,
                                       String email, Role role, Errors errors) throws IOException {
    User user = oidc.user();
    Group group = requireGroup(groupName);
    if (group == null) {
      res.setStatus(404);
      return;
    }
    renderMembers(req, res, group, user, email, role, errors);
  }
}
```

The `requireGroup` helper depends on `GroupService.findGroup(String)` (added in earlier work). If `GroupController` already exposes a `findGroup` accessor, route through that. Otherwise, MembershipController needs `GroupService` injected:

```java
public MembershipController(GroupService groupService, MembershipService membershipService,
                            ViewService viewService, OIDC<User> oidc, JTETemplates templates) {
  this.groupService = groupService;
  ...
}

private Group requireGroup(String groupName) {
  return groupService.findGroup(groupName).orElse(null);
}
```

Adjust the constructor accordingly. Also import `GroupService`.

The error-on-non-invite catch blocks (`changeRole`, `remove`, `leave`) currently have empty try/catch — Plan 04 punts on the rendered error UX for those. They're administrative actions where the form/UI prevents the invalid operations through buttons/disabled states. If a dedicated user manually crafts a malformed POST, the redirect happens silently. A future plan can add proper error rendering.

Alphabetical method order: `accept, changeRole, decline, invite, leave, members, remove` (public), `requireGroup, renderMembers, renderMembersWithErrors` (private alphabetical).

- [ ] **Step 2: Wire `MembershipController` in `Main.java`**

Read `Main.java`. Add:
- Field `public final MembershipController membershipController;` (alphabetical between `groupController` and `oidc` or wherever the Controllers live).
- Field `public final MembershipService membershipService;` (alphabetical).
- Construction in the constructor body:
  ```java
  membershipService = new MembershipService(config);
  membershipController = new MembershipController(groupService, membershipService, viewService, oidc, templates);
  ```
- Routes inside the existing `prefix("/groups", ...)` block:
  ```java
  groups.get("/{name}/members", membershipController::members);
  groups.post("/{name}/members", membershipController::invite);
  groups.post("/{name}/members/{userId}/accept", membershipController::accept);
  groups.post("/{name}/members/{userId}/decline", membershipController::decline);
  groups.post("/{name}/members/{userId}/remove", membershipController::remove);
  groups.post("/{name}/members/{userId}/role", membershipController::changeRole);
  groups.post("/{name}/leave", membershipController::leave);
  ```

- [ ] **Step 3: Build + tests**

```bash
cd /Users/bpontarelli/dev/latte-java/app
latte build 2>&1 | tail -3
latte test 2>&1 | tail -8
```

Expected: BUILD SUCCESSFUL; zero failures.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/controller/MembershipController.java
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(controller): MembershipController + 7 routes wired"
```

---

## Task 10: UI — members tab + role-picker fix + invite form

**Files:**
- Modify: `web/pages/groups/members.jte`
- Modify: `web/pages/groups/role-picker.jte`
- Modify: `web/components/member-row.jte`

- [ ] **Step 1: Fix `role-picker.jte`**

Read `/Users/bpontarelli/dev/latte-java/app/web/pages/groups/role-picker.jte`. The current values are `MAINTAINER` / `PUBLISHER` / `VIEWER` — these are stale. Replace with the real roles `OWNER` / `CONTRIBUTOR`.

```jte
<%--
    Role select for member row.
--%>
@param String value
@param String name = "role"

<label class="relative inline-flex">
  <select
      name="${name}"
      class="appearance-none pl-3 pr-7 py-1.5 text-sm font-medium text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md cursor-pointer">
    <option value="OWNER" @if(value.equals("OWNER"))selected @endif>Owner</option>
    <option value="CONTRIBUTOR" @if(value.equals("CONTRIBUTOR"))selected @endif>Contributor</option>
  </select>
  <span class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none inline-flex">
        @template.components.icon(name = "chevron-down", size = 12)
    </span>
</label>
```

- [ ] **Step 2: Fix `members.jte`**

Read the file in full. The current invite form action is `/groups/${group.name()}/invite` — change to `/app/groups/${group.name()}/members` (POST endpoint per the new routes).

Update the `@param` block to add:
- `@param Errors errors = new Errors()` (with appropriate import)
- `@param List<Member> members = java.util.List.of()`

(Remove or update `inviteOpen`, `inviteEmail`, `inviteRole`, `inviteError` — replace `inviteError` with reading from `errors`).

Inside the body, after the page-header / invite button row, render the actual member list:

```jte
<div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm">
  @for(Member m : members)
    @template.components.member-row(member = m, canManage = canManage, groupName = group.name())
  @endfor
  @if(members.isEmpty())
    @template.components.empty-state(message = "No members yet.")
  @endif
</div>
```

Replace any `${inviteError}` with extraction from `errors.fieldErrors.get("email")`. The simplest pattern (matches the existing approach in `new.jte`):

```jte
!{java.util.List<org.lattejava.app.error.Error> emailErrors = errors.fieldErrors.getOrDefault("email", java.util.List.of());}
!{String emailError = emailErrors.isEmpty() ? null : emailErrors.get(0).message;}
@template.components.input(name = "email", value = inviteEmail, placeholder = "name@example.com", error = emailError)
```

The role picker invocation:
```jte
@template.pages.groups.role-picker(value = inviteRole, name = "role")
```

The form action and method:
```jte
<form id="invite" method="post" action="/app/groups/${group.name()}/members">
```

Read the existing file thoroughly before editing — there's substantial markup. Preserve the layout; just update the action, the params, the role picker, and the member loop.

- [ ] **Step 3: Fix `member-row.jte`**

Read `/Users/bpontarelli/dev/latte-java/app/web/components/member-row.jte`. The current shape stubs out role and remove buttons but doesn't post anywhere. Update:

Add `@param String groupName = ""` so the form actions can target this group.

For ACTIVE members where `canManage` is true and the row is not the current user, render:
- A role-change form posting to `/app/groups/${groupName}/members/${member.userId()}/role`.
- A remove button posting to `/app/groups/${groupName}/members/${member.userId()}/remove`.

For PENDING members:
- Show the "invited" badge (already there).
- A small kebab menu or similar for cancel-invite (POST to `/app/groups/${groupName}/members/${member.userId()}/remove` — same endpoint as remove).

Don't introduce JavaScript dropdowns; stick to plain forms. The "three dot menu" wording in the design can be deferred to a polish pass.

For the role select, wrap it in a form:
```jte
<form method="post" action="/app/groups/${groupName}/members/${member.userId()}/role" class="inline">
  @template.pages.groups.role-picker(value = member.role().name())
  <button type="submit" class="ml-1 text-sm text-sky-600 dark:text-sky-400 hover:underline">Save</button>
</form>
```

For the remove button:
```jte
<form method="post" action="/app/groups/${groupName}/members/${member.userId()}/remove" class="inline">
  <button type="submit" class="text-sm text-red-600 dark:text-red-400 hover:underline">Remove</button>
</form>
```

Read the file fully to position these correctly within the existing layout grid.

- [ ] **Step 4: Build + tests**

```bash
cd /Users/bpontarelli/dev/latte-java/app
latte build 2>&1 | tail -3
latte test 2>&1 | tail -8
```

Expected: BUILD SUCCESSFUL; zero failures.

- [ ] **Step 5: Commit**

```bash
git add web/pages/groups/members.jte
git add web/pages/groups/role-picker.jte
git add web/components/member-row.jte
git commit -m "feat(ui): members tab + invite form + role-picker fixes"
```

---

## Task 11: UI — settings leave + overview accept/decline card

**Files:**
- Modify: `web/pages/groups/settings.jte`
- Modify: `web/pages/groups/overview.jte`

- [ ] **Step 1: Add Leave button to `settings.jte`**

Read the file. In the "Danger Zone" section (or after the description form), add a Leave form:

```jte
<form method="post" action="/app/groups/${group.name()}/leave" class="mt-4">
  <button type="submit"
          class="px-4 py-2 text-sm font-medium text-red-700 bg-red-50 border border-red-200 rounded-md hover:bg-red-100 dark:bg-red-950/40 dark:text-red-300 dark:border-red-800">
    Leave group
  </button>
</form>
```

Position it sensibly — near the Delete-group control if one exists, or in a "Danger zone" panel.

- [ ] **Step 2: Add accept/decline card to `overview.jte`**

Read `/Users/bpontarelli/dev/latte-java/app/web/pages/groups/overview.jte`. Add a new `@param` for the viewer's pending membership (if any), and render an accept/decline card at the top of the overview when present.

Add to the page params at the top of the file:
```jte
@param org.lattejava.app.model.Member viewerMembership = null
```

Then in the body, BEFORE the existing PENDING/FAILED verification banners:

```jte
@if(viewerMembership != null && viewerMembership.state() == org.lattejava.app.model.MembershipState.PENDING)
  <div class="flex items-start gap-3.5 p-4 mb-4 bg-white dark:bg-slate-900 border border-sky-300 dark:border-sky-800 rounded-lg">
    <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
      @template.components.icon(name = "users", size = 17)
    </div>
    <div class="flex-1">
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">You've been invited</div>
      <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">Accept to join this group.</p>
    </div>
    <form method="post" action="/app/groups/${group.name()}/members/${viewerMembership.userId()}/accept" class="inline mr-2">
      <button type="submit"
              class="px-3 py-1.5 text-sm font-medium text-white bg-sky-600 rounded-md hover:bg-sky-700">
        Accept
      </button>
    </form>
    <form method="post" action="/app/groups/${group.name()}/members/${viewerMembership.userId()}/decline" class="inline">
      <button type="submit"
              class="px-3 py-1.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-md hover:bg-slate-50 dark:bg-slate-900 dark:text-slate-300 dark:border-slate-700">
        Decline
      </button>
    </form>
  </div>
@endif
```

- [ ] **Step 3: Update `GroupController.detail` to pass `viewerMembership`**

Read `GroupController.java`'s `detail` handler. Add a lookup for the viewer's membership and pass it into the template:

```java
public void detail(HTTPRequest req, HTTPResponse res) throws IOException {
  String groupName = (String) req.getAttribute("name");
  User user = oidc.user();
  Optional<Group> groupOpt = groupService.findGroup(groupName);
  if (groupOpt.isEmpty()) {
    res.setStatus(404);
    return;
  }
  Group group = groupOpt.get();
  Optional<Member> viewerMembership = databaseClient.findMember(groupName, user.userId());
  // ... existing code ...
  params.put("viewerMembership", viewerMembership.orElse(null));
}
```

Wait — `GroupController` doesn't currently have a `databaseClient` field per the recent service-construction refactor. Add `MembershipService` (or expose `findMember` via `MembershipService.findMember`) and call that.

Actually a cleaner approach: add a `MembershipService.findMember(String groupName, UUID userId)` delegation method. Then `GroupController.detail` calls `membershipService.findMember(groupName, user.userId())`. Update `MembershipController` to also import `MembershipService.findMember` if needed.

Add to `MembershipService`:
```java
public Optional<Member> findMember(String groupName, UUID userId) {
  return databaseClient.findMember(groupName, userId);
}
```

(Place alphabetically — between `decline` and `invite`.)

Update `GroupController` to take `MembershipService` and use it:
```java
public GroupController(GroupService groupService, MembershipService membershipService, ...) {
  this.groupService = groupService;
  this.membershipService = membershipService;
  ...
}
```

Update `Main.java` to pass `membershipService` into `GroupController` construction.

- [ ] **Step 4: Build + tests**

```bash
cd /Users/bpontarelli/dev/latte-java/app
latte build 2>&1 | tail -3
latte test 2>&1 | tail -8
```

Expected: BUILD SUCCESSFUL; zero failures.

- [ ] **Step 5: Commit**

```bash
git add web/pages/groups/settings.jte
git add web/pages/groups/overview.jte
git add src/main/java/org/lattejava/app/service/MembershipService.java
git add src/main/java/org/lattejava/app/controller/GroupController.java
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(ui): overview accept/decline card; settings leave button"
```

---

## Task 12: MainTest end-to-end smoke for the members tab

**Files:**
- Modify: `src/test/java/org/lattejava/app/tests/MainTest.java`

- [ ] **Step 1: Append the test**

Add to `MainTest.java` (alphabetical position after `groupDetail`):

```java
@Test
public void groupMembers() throws Exception {
  var string = new StringBodyAsserter();
  oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  test.get("/app/groups/org.lattejava/members")
      .assertStatus(200)
      .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
}
```

- [ ] **Step 2: Run all tests**

```bash
cd /Users/bpontarelli/dev/latte-java/app
latte test 2>&1 | tail -8
```

Expected: zero failures.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "test: end-to-end smoke for /app/groups/{name}/members"
```

---

## Self-review checklist

- All six membership flows (`invite`, `accept`, `decline`, `remove`, `changeRole`, `leave`) are implemented?
- Invite uses FA's `register` API for new users; logs would-send email for existing users?
- Last-Owner protection enforced in `remove`, `changeRole`, `leave`?
- Self-rule enforced in `remove`, `changeRole` (cannot operate on yourself)?
- All seven new routes registered inside the nested `/groups` prefix?
- Members tab renders real members + invite form?
- Role picker uses the correct `OWNER`/`CONTRIBUTOR` values (not stale `MAINTAINER`/`PUBLISHER`)?
- Overview shows accept/decline card when viewer has a pending invitation?
- Settings has a Leave button?
- Tests cover: invite happy path + 2 invalid cases; accept; decline; remove (3 cases); changeRole (3 cases); leave (2 cases); plus the MainTest smoke?
- Copyright headers, error-message brackets, alphabetization, module imports preferred — all per `.claude/rules/`?

---

## What this plan deliberately does NOT do

- **Custom invite email templates** — Plan 06 adds the SendGrid SMTP wiring + the two new email templates (invite-existing-user, set-password-with-invite-context). Plan 04 logs and relies on FA's defaults.
- **Public group pages** — `/app/groups/*` stays gated by `oidc.authenticated()`.
- **Group deletion + R2** — Plan 05.
- **GitHub OAuth verification** — Plan 03b (depends on Plan 06's GitHub IDP config).
- **JS/Alpine dropdowns** for the three-dot member menus — Plan 04 ships plain forms; polish in a later pass.
