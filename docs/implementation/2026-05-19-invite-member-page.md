# Invite Member Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken inline "Invite member" form with a dedicated full-page invite form (mirroring the New group page) that uses generic selectable role cards.

**Architecture:** A new GET route `/app/groups/{groupName}/members/invite` renders `web/pages/groups/invite.jte` via `MembershipController.inviteForm`. A shared private helper `renderInviteForm` is used by both the GET handler and the existing POST `invite` handler's validation-error branch (self-resubmit pattern, same as `GroupController.create`). The role selector is a new general-purpose `web/components/radio-card.jte` component. The old inline form and its parameter plumbing through `detail.jte` → `members.jte` are removed.

**Tech Stack:** Java 25 (JPMS), JTE 3.x templates, Tailwind v4, TestNG + `org.lattejava.web` `WebTest`/`OIDCTestFixture` integration tests against a running `Main`, FusionAuth on `:9013`, Cloudflare D1.

**Spec:** `docs/design/2026-05-19-invite-member-page-design.md`

---

## Preconditions / how to run tests

The suite boots a real `Main` and drives FusionAuth end-to-end. Before running tests:

- FusionAuth must be running locally on `:9013` with the kickstart applied (`cd src/main/fusionauth && docker compose --profile mailcatcher up -d`).
- Network access to your Cloudflare D1 is required.
- No dev server may be bound to `:8080` (it makes the whole suite skip).

Build: `latte build`
Run this plan's tests: `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Run full suite (Task 3 regression): `latte test`

If the working environment cannot start Docker/FusionAuth, run the `latte` commands in the user's interactive shell (prefix with `!`).

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `web/components/radio-card.jte` | Generic single-choice card backed by a radio input. No domain knowledge. | Create |
| `web/pages/groups/invite.jte` | Full-page invite form (breadcrumb, header, email input, two role radio-cards, buttons). | Create |
| `src/main/java/org/lattejava/app/controller/MembershipController.java` | Add `inviteForm`, add `renderInviteForm` helper, rework `invite` error branch, simplify `renderMembers`/`list`. | Modify |
| `src/main/java/org/lattejava/app/Main.java` | Register `GET /{groupName}/members/invite`. | Modify |
| `web/pages/groups/members.jte` | Repoint the "Invite member" button; delete the inline invite form + its params. | Modify |
| `web/pages/groups/detail.jte` | Stop threading `inviteEmail`/`inviteRole`/`errors` into the members component. | Modify |
| `src/test/java/org/lattejava/app/tests/InviteFlowTest.java` | HTTP-level coverage of the new page and flows. | Create |

---

## Task 1: Invite form page (component + template + GET route)

This task is one testable unit: the page cannot render until the component, template, controller method, helper, and route all exist.

**Files:**
- Create: `web/components/radio-card.jte`
- Create: `web/pages/groups/invite.jte`
- Modify: `src/main/java/org/lattejava/app/controller/MembershipController.java`
- Modify: `src/main/java/org/lattejava/app/Main.java`
- Test: `src/test/java/org/lattejava/app/tests/InviteFlowTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/lattejava/app/tests/InviteFlowTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import org.lattejava.app.model.Group;

import static org.testng.Assert.*;

/**
 * HTTP-level coverage of the dedicated invite-member page: the form renders with both role cards, a missing group
 * is 303-redirected home by GroupSecurity, a blank email re-renders the invite page with the field error, a valid
 * email creates a pending member and redirects to the members list, and the members list links to the new page.
 */
@Test
public class InviteFlowTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  @Test
  public void inviteForm_missingGroup_redirectsHome() throws Exception {
    // GroupSecurity (installed at the /app/groups prefix) sends missing-group requests to /app/ with a 303
    // before the controller runs.
    oidc.login("test@lattejava.org", "password", APP_ID);
    test.get("/app/groups/test.invite.missing/members/invite")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void inviteForm_rendersFormAndRoleCards() throws Exception {
    String name = "test.invite.form";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/members/invite")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Invite a member")
                                       .contains("action=\"/app/groups/" + name + "/members/invite\"")
                                       .contains("name=\"email\"")
                                       .contains("name=\"role\"")
                                       .contains("value=\"CONTRIBUTOR\"")
                                       .contains("value=\"OWNER\"")
                                       .contains("Contributor")
                                       .contains("Owner"));
    } finally {
      db.deleteGroup(name);
    }
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: `inviteForm_rendersFormAndRoleCards` FAILS (route not registered → not 200) and/or `inviteForm_missingGroup_redirectsHome` may pass incidentally (unknown route). The suite must compile and the render test must fail.

- [ ] **Step 3: Create the generic `radio-card` component**

Create `web/components/radio-card.jte`:

```jte
<%--
    Generic single-choice card backed by a radio input. Knows nothing about any
    specific domain — the consumer supplies the value, text, icon, and checked state.
    The whole card is clickable because the visible content is inside the <label>.
--%>
@param String name
@param String value
@param String label
@param String description = ""
@param String icon = ""
@param boolean checked = false

<label class="relative block cursor-pointer">
  <input type="radio" name="${name}" value="${value}" checked="${checked}" class="peer sr-only">
  <span aria-hidden="true"
        class="absolute top-4 right-4 w-4 h-4 rounded-full border-2 border-slate-300 dark:border-slate-600 transition-colors peer-checked:border-sky-500 peer-checked:bg-sky-500"></span>
  <div class="flex flex-col gap-2 h-full p-4 pr-10 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg transition-colors peer-checked:border-sky-500 peer-checked:ring-2 peer-checked:ring-sky-500/30 peer-focus-visible:ring-2 peer-focus-visible:ring-sky-500/40">
    <div class="flex items-center gap-2.5">
      @if(!icon.isEmpty())
        <div class="inline-flex items-center justify-center w-9 h-9 rounded-lg bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 shrink-0">
          @template.components.icon(name = icon, size = 18)
        </div>
      @endif
      <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">${label}</div>
    </div>
    @if(!description.isEmpty())
      <p class="m-0 text-sm text-slate-600 dark:text-slate-300">${description}</p>
    @endif
  </div>
</label>
```

Note: the indicator dot and the card body are both direct siblings of the `peer` input, so `peer-checked:` resolves correctly (Tailwind's `peer-*` only matches siblings, not descendants). JTE renders `checked="${checked}"` as the bare `checked` attribute when true and omits it when false — same pattern as `web/pages/groups/role-picker.jte`'s `selected`.

- [ ] **Step 4: Create the invite page template**

Create `web/pages/groups/invite.jte` (structure mirrors `web/pages/groups/new.jte`):

```jte
@import org.lattejava.app.model.view.MainView
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.InviteRequest
@import org.lattejava.app.model.Role
@import org.lattejava.app.error.Error
@import org.lattejava.app.error.Errors
@import java.util.List
@param MainView view
@param Group group
@param InviteRequest request
@param Errors errors = new Errors()

!{List<Error> emailErrors = errors.fieldErrors.getOrDefault("email", java.util.List.of());}
!{String emailError = emailErrors.isEmpty() ? null : emailErrors.get(0).message;}

@template.layout.main(view=view, pageTitle="Invite a member", activeNav="groups", activeGroupId=group.name(),
content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name="chevron-right", size=11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/${group.name()}/members/">${group.name()}</a>
    @template.components.icon(name="chevron-right", size=11)
    <span>Invite</span>
  </nav>
  @template.components.page-header(title="Invite a member", subtitle="They'll get an email and stay invited until they accept.")
  @if(!errors.generalErrors.isEmpty())
    <div class="mb-4 p-4 border border-red-300 bg-red-50 dark:bg-red-950/40 rounded-lg text-sm text-red-700 dark:text-red-300">
      @for(Error err : errors.generalErrors)
        <p class="m-0">${err.message}</p>
      @endfor
    </div>
  @endif

  <form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/invite">
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Email</h3>
    @template.components.input(name="email", value=request.email(), type="email", placeholder="teammate@company.com", required=true, autofocus=true, error=emailError)
    <div class="h-6"></div>
    <h3 class="m-0 mb-3 text-base font-bold text-slate-900 dark:text-slate-100">Role</h3>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      @template.components.radio-card(name="role", value="CONTRIBUTOR", icon="users", label=Role.CONTRIBUTOR.label(), description=Role.CONTRIBUTOR.description(), checked=request.role() == Role.CONTRIBUTOR)
      @template.components.radio-card(name="role", value="OWNER", icon="shield", label=Role.OWNER.label(), description=Role.OWNER.description(), checked=request.role() == Role.OWNER)
    </div>
    <div class="h-6"></div>
    <div class="flex gap-2 justify-end">
      @template.components.button(label = "Cancel", variant = "ghost", href = "/app/groups/" + group.name() + "/members/")
      @template.components.button(label = "Send invitation", variant = "primary", icon = "send", type = "submit")
    </div>
  </form>
`)
```

- [ ] **Step 5: Add `inviteForm` + `renderInviteForm` to the controller**

In `src/main/java/org/lattejava/app/controller/MembershipController.java`, add the public `inviteForm` method immediately after `invite` (alphabetical order: `invite` < `inviteForm` < `leave`):

```java
  public void inviteForm(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    renderInviteForm(req, res, groupName, new InviteRequest(groupName, "", Role.CONTRIBUTOR), new Errors());
  }
```

Add the private helper `renderInviteForm` in the private-methods section, before `renderMembers` (alphabetical: `renderInviteForm` < `renderMembers`):

```java
  private void renderInviteForm(HTTPRequest req, HTTPResponse res, String groupName,
                                InviteRequest request, Errors errors) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/invite.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "request", request,
            "errors", errors
        )
    );
  }
```

(`InviteRequest`, `Role`, `Errors`, `Group`, `MainView`, `Optional`, `Map` all resolve through the existing `import module` statements / model package already used by this controller — no new imports needed.)

- [ ] **Step 6: Register the GET route**

In `src/main/java/org/lattejava/app/Main.java`, in the `/{groupName}/members` prefix block, add the `get("/invite", ...)` route immediately before the existing `post("/invite", ...)`:

```java
        .prefix("/{groupName}/members", membersRoute -> {
          MembershipController members = new MembershipController(oidc, templates);
          membersRoute.get("/", members::list)
                      .get("/invite", members::inviteForm)
                      .post("/invite", members::invite)
                      .post("/{userId}/accept", members::accept)
                      .post("/{userId}/decline", members::decline)
                      .post("/{userId}/remove", members::remove)
                      .post("/{userId}/role", members::changeRole)
                      .post("/leave", members::leave);
        });
```

(Match the exact existing surrounding lines in `Main.java`; only the `.get("/invite", members::inviteForm)` line is added. Route registration order is otherwise unchanged.)

- [ ] **Step 7: Build and run the tests to verify they pass**

Run: `latte build` then `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: `inviteForm_rendersFormAndRoleCards` PASS, `inviteForm_missingGroup_redirectsHome` PASS.

- [ ] **Step 8: Commit**

```bash
git add web/components/radio-card.jte web/pages/groups/invite.jte \
        src/main/java/org/lattejava/app/controller/MembershipController.java \
        src/main/java/org/lattejava/app/Main.java \
        src/test/java/org/lattejava/app/tests/InviteFlowTest.java
git commit -m "Invite member: full-page form with generic radio-card role selector

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: POST invite re-renders the invite page on validation error

The POST `/invite` handler currently re-renders the members tab (`renderMembers`) on `ValidationException`. Switch it to re-render the full invite page via `renderInviteForm`, reusing the exact `InviteRequest` already built for the service call.

**Files:**
- Modify: `src/main/java/org/lattejava/app/controller/MembershipController.java` (`invite` method)
- Test: `src/test/java/org/lattejava/app/tests/InviteFlowTest.java`

- [ ] **Step 1: Add the failing tests**

Add these two methods to `src/test/java/org/lattejava/app/tests/InviteFlowTest.java`:

```java
  @Test
  public void invite_blankEmail_rerendersInviteForm() throws Exception {
    String name = "test.invite.blank";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.withForm(Map.of("email", "", "role", "CONTRIBUTOR"))
          .post("/app/groups/" + name + "/members/invite")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("An email address is required.")
                                       .contains("action=\"/app/groups/" + name + "/members/invite\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void invite_validEmail_redirectsToMembersAndCreatesPendingMember() throws Exception {
    String name = "test.invite.create";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    String email = "test+invite-page-" + UUID.randomUUID() + "@lattejava.org";
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.withForm(Map.of("email", email, "role", "CONTRIBUTOR"))
          .post("/app/groups/" + name + "/members/invite")
          .assertRedirect(303, "/app/groups/" + name + "/members/");

      var members = db.findMembers(name);
      assertEquals(members.size(), 1);
      assertEquals(members.getFirst().state(), MembershipState.PENDING);
      assertEquals(members.getFirst().role(), Role.CONTRIBUTOR);
    } finally {
      db.deleteGroup(name);
    }
  }
```

Note on `db.findMembers(name)`: this mirrors the read used by `MembershipService.listMembers` against the `members` table for a group. If `DatabaseClient` exposes a differently named accessor for "all members of a group" (confirm by reading `src/main/java/org/lattejava/app/db/DatabaseClient.java`), use that exact method name here instead — do not invent one. The behavioral assertion (303 redirect + one PENDING CONTRIBUTOR member) stays the same.

- [ ] **Step 2: Run the tests to verify the new behavior fails**

Run: `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: `invite_blankEmail_rerendersInviteForm` FAILS — the current `invite` catch calls `renderMembers`, so the response is the members tab (no `action="/app/groups/.../members/invite"` form, and the blank-email error is rendered in the old inline form context, not guaranteed). `invite_validEmail_...` should already PASS (success path unchanged) — that is the regression guard.

- [ ] **Step 3: Rework the `invite` error branch**

In `src/main/java/org/lattejava/app/controller/MembershipController.java`, replace the existing `invite` method body so it builds the `InviteRequest` once and re-renders the invite page on validation failure:

```java
  public void invite(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    String email = req.getParameter("email");
    String roleParam = req.getParameter("role");
    Role role = roleParam == null ? Role.CONTRIBUTOR : Role.valueOf(roleParam);
    InviteRequest request = new InviteRequest(groupName, email, role);
    User current = oidc.user();
    try {
      membershipService.invite(request, current);
      res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
    } catch (ValidationException e) {
      renderInviteForm(req, res, groupName, request, e.errors());
    }
  }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: all four `InviteFlowTest` methods PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/controller/MembershipController.java \
        src/test/java/org/lattejava/app/tests/InviteFlowTest.java
git commit -m "Invite member: POST re-renders the full invite page on validation error

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Repoint the button and remove the dead inline form + param plumbing

`renderMembers` and the `detail.jte` → `members.jte` chain still carry `inviteEmail`/`inviteRole`. With the inline form gone these are dead. The "Invite member" button still links to `?invite=1#invite`. All three template/controller changes must land together: removing the `errors`/`inviteEmail`/`inviteRole` params from `members.jte` while `detail.jte` still passes them would be a JTE "unknown parameter" error.

**Files:**
- Modify: `web/pages/groups/members.jte`
- Modify: `web/pages/groups/detail.jte`
- Modify: `src/main/java/org/lattejava/app/controller/MembershipController.java` (`renderMembers`, `list`)
- Test: `src/test/java/org/lattejava/app/tests/InviteFlowTest.java`

- [ ] **Step 1: Add the failing test**

Add this method to `src/test/java/org/lattejava/app/tests/InviteFlowTest.java` (uses the suite-seeded `org.lattejava` group, of which the test user is an `OWNER` — no DB writes/cleanup needed):

```java
  @Test
  public void membersList_inviteButtonLinksToInvitePage() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", APP_ID);
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("href=\"/app/groups/org.lattejava/members/invite\"")
                                     .doesNotContain("?invite=1#invite"));
  }
```

If `StringBodyAsserter` has no `doesNotContain` method (confirm in `org.lattejava.web`), drop that one chained call and keep only the positive `contains` assertion — do not invent an API.

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: `membersList_inviteButtonLinksToInvitePage` FAILS — the button still renders `href="?invite=1#invite"`.

- [ ] **Step 3: Repoint the button and delete the inline form in `members.jte`**

Edit `web/pages/groups/members.jte`:

1. Remove the now-unused param declarations and derived locals. Delete these lines:
   - `@param String inviteEmail = ""`
   - `@param String inviteRole = "CONTRIBUTOR"`
   - `@param Errors errors = new Errors()`
   - `@import org.lattejava.app.error.Errors`
   - the two `!{...}` lines computing `emailErrors` / `emailError`

2. Change the button from:

   ```jte
   @template.components.button(label = "Invite member", variant = "primary", icon = "plus", href = "?invite=1#invite")
   ```

   to:

   ```jte
   @template.components.button(label = "Invite member", variant = "primary", icon = "plus", href = "/app/groups/" + group.name() + "/members/invite")
   ```

3. Delete the entire inline invite form block — the `@if(!errors.empty() || !inviteEmail.isEmpty())` line through its matching `@endif` (the `<form id="invite" ...>` ... `</form>` region), inclusive.

The static role-explanation grid at the bottom (`@for(Role r : Role.values())`) stays — it is out of scope and uses no invite params.

The file's remaining used imports are `MainView`, `Group`, `Member`, `MembershipState`, `Role`, `List`. Keep them.

- [ ] **Step 4: Stop passing the dead params from `detail.jte`**

In `web/pages/groups/detail.jte`:

1. Delete the param declarations `@param String inviteEmail = ""` and `@param String inviteRole = "CONTRIBUTOR"`.
2. Change the members component call from:

   ```jte
   @template.pages.groups.members(view = view, group = group, activeTab = activeTab, inviteEmail = inviteEmail, inviteRole = inviteRole, errors = errors, members = members)
   ```

   to:

   ```jte
   @template.pages.groups.members(view = view, group = group, activeTab = activeTab, members = members)
   ```

Keep `@param Errors errors = new Errors()` and the `@template.pages.groups.settings(group = group, errors = errors)` call unchanged — the settings tab still uses `errors`.

- [ ] **Step 5: Simplify `renderMembers` and `list` in the controller**

In `src/main/java/org/lattejava/app/controller/MembershipController.java`:

Replace `list`:

```java
  public void list(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    renderMembers(req, res, groupName);
  }
```

Replace `renderMembers` (drop the `inviteEmail`/`inviteRole` params and their `Map` entries; `detail.jte`'s `errors` param has a default so it is omitted here):

```java
  private void renderMembers(HTTPRequest req, HTTPResponse res, String groupName) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    MainView view = viewService.buildMainView(user);
    var members = membershipService.listMembers(groupName);
    templates.html("pages/groups/detail.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "activeTab", "members",
            "members", members
        )
    );
  }
```

Confirm no other caller of `renderMembers` remains (after Task 2, only `list` calls it). If the compiler reports another caller, stop and re-evaluate — do not add an overload.

- [ ] **Step 6: Build and run this plan's tests**

Run: `latte build` then `latte test --test=org.lattejava.app.tests.InviteFlowTest`
Expected: all five `InviteFlowTest` methods PASS.

- [ ] **Step 7: Run the full suite (regression)**

Run: `latte test`
Expected: full suite PASS. Pay attention to existing members/detail tests (`MainTest`, `VerificationFlowTest`, `MembershipServiceTest`) — a broken `detail.jte`/`members.jte` param contract would surface here.

- [ ] **Step 8: Commit**

```bash
git add web/pages/groups/members.jte web/pages/groups/detail.jte \
        src/main/java/org/lattejava/app/controller/MembershipController.java \
        src/test/java/org/lattejava/app/tests/InviteFlowTest.java
git commit -m "Invite member: repoint button to invite page, remove dead inline form

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Rebuild Tailwind so the new classes are emitted

`web/components/radio-card.jte` and `web/pages/groups/invite.jte` introduce class combinations (e.g. `peer-checked:*`, `peer-focus-visible:*`, `pr-10`) that must be present in the committed-out `web/static/css/app.css`. This does not affect test pass/fail (templates render without CSS) but is required for the page to look correct.

**Files:**
- Modify: `web/static/css/app.css` (generated)

- [ ] **Step 1: Regenerate the stylesheet**

Run the Tailwind build (one-shot; `latte tailwind` is the watch target). Use the project's Tailwind target/CLI as documented in `CLAUDE.md`. If only the watch target exists, start it, let it produce one rebuild, then stop it.

Run: `latte tailwind` (stop after the first rebuild) — or the project's equivalent one-shot Tailwind command.
Expected: `web/static/css/app.css` updated; `git diff --stat web/static/css/app.css` shows changes.

- [ ] **Step 2: Sanity-check the new utilities are present**

Run: `grep -c "peer-checked" web/static/css/app.css`
Expected: count ≥ 1.

- [ ] **Step 3: Commit**

```bash
git add web/static/css/app.css
git commit -m "Invite member: rebuild Tailwind for radio-card + invite page styles

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**

- Spec §1 (route + controller) → Task 1 Steps 5–6.
- Spec §2 (generic `radio-card.jte`) → Task 1 Step 3.
- Spec §3 (`invite.jte` taking `InviteRequest`) → Task 1 Step 4.
- Spec §4 (repoint button, delete inline form) → Task 3 Steps 3.
- Spec §5 (POST error re-renders invite page, reuse same `InviteRequest`) → Task 2 Step 3.
- Spec §6 (cleanup `renderMembers`/`detail.jte`/`members.jte` param plumbing) → Task 3 Steps 3–5.
- Spec "Testing" bullets (GET 200 + role cards, POST valid 303 + PENDING member, POST invalid re-render, GET missing group 303 → /app/ via GroupSecurity) → Task 1 / Task 2 / Task 3 tests. Tailwind note → Task 4.

**Placeholder scan:** No TBD/TODO. Two explicit "verify the real API name before use" notes (`db.findMembers`, `StringBodyAsserter.doesNotContain`) instruct reading the source rather than guessing — these are verification instructions, not placeholders, because the behavioral assertions are fully specified regardless.

**Type consistency:** `renderInviteForm(req, res, groupName, InviteRequest, Errors)` is defined once (Task 1 Step 5) and called with that exact signature from `inviteForm` (Task 1) and the `invite` catch (Task 2). `renderMembers` is reduced to `(req, res, groupName)` and its only remaining caller `list` is updated in the same task (Task 3 Step 5). Template param `request` (type `InviteRequest`) in `invite.jte` matches the `"request"` key supplied by `renderInviteForm`. Component `@template.components.radio-card` name matches the created file `web/components/radio-card.jte`.

---

## Execution Handoff

(Filled in by the writing-plans skill after save.)
