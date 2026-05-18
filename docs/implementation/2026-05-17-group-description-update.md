# Group Description Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing group settings form work — `POST /app/groups/{name}/settings` updates a group's description, with a 500-char limit enforced by a single shared validator used by both the new update path and the existing create path.

**Architecture:** Controller → service → validation, per `.claude/rules/web-conventions.md`. A new `GroupController.updateSettings` handler calls `GroupService.updateDescription`, which validates via a shared `GroupValidator` helper and persists through a new `DatabaseClient.updateGroupDescription`. The 500-char rule lives in one private `GroupValidator.validateDescription` method called from both `validateUpdateDescription` (new) and `validate(Group)` (existing create path). No permission checks — those are deferred to a separate permissions-middleware feature.

**Tech Stack:** Java 25 (JPMS, module imports), `latte` build tool, Cloudflare D1 over REST, JTE 3.x templates, TestNG + `WebTest`/`OIDCTestFixture`.

**Spec:** `docs/design/2026-05-16-group-description-update-design.md`

---

### Prerequisites (read before starting)

- FusionAuth running locally on `:9011` with kickstart applied, and D1 network access — the TestNG suite boots a real server and hits the real DB (see `CLAUDE.md`). Every test task below requires this.
- Build: `latte build`. Full suite: `latte test`. Single test class: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`.
- TestNG has no per-method filter here; `--test=` takes a class. "Run the test" steps below run the whole class and you confirm the named method's result in the output.
- No new external dependencies → no `project.latte` or `module-info.java` changes.
- All paths are relative to the repo root (the worktree). Java sources use `import module` declarations; follow the existing style in each file you edit.

---

### Task 1: Shared description validator in `GroupValidator`

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/validation/GroupValidator.java`
- Test: `src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java`

- [ ] **Step 1: Write the failing tests**

Add these methods to `GroupValidatorTest` (class already has `validator` wired in `@BeforeClass`; `import static org.testng.Assert.*;` is already present):

```java
  @Test
  public void updateDescription_acceptsEmpty() {
    assertTrue(validator.validateUpdateDescription("").empty());
    assertTrue(validator.validateUpdateDescription(null).empty());
  }

  @Test
  public void updateDescription_acceptsAtLimit() {
    assertTrue(validator.validateUpdateDescription("x".repeat(500)).empty());
  }

  @Test
  public void updateDescription_rejectsTooLong() {
    Errors errors = validator.validateUpdateDescription("x".repeat(501));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("description", "[tooLong]description"));
  }

  @Test
  public void updateDescription_trimsBeforeMeasuring() {
    // 500 real chars + surrounding whitespace must still pass (trimmed length == 500).
    assertTrue(validator.validateUpdateDescription("  " + "x".repeat(500) + "  ").empty());
  }

  @Test
  public void validate_rejectsTooLongDescriptionOnCreate() {
    Group g = new Group("io.github.toolongdesc", "x".repeat(501), GroupState.PENDING, null, Instant.EPOCH, null);
    Errors errors = validator.validate(g);
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("description", "[tooLong]description"));
  }
```

`Errors` and `Group`/`GroupState` resolve through the existing `import module org.lattejava.app;` in the test file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: compilation failure — `validateUpdateDescription` does not exist yet. (That is the failing state for this TDD step.)

- [ ] **Step 3: Implement the shared helper and public method**

In `GroupValidator.java`, add the shared private helper and the new public method. Place the public method directly after `validate(Group group)` and the private helper after it:

```java
  public Errors validateUpdateDescription(String description) {
    Errors errors = new Errors();
    validateDescription(errors, description);
    return errors;
  }

  private void validateDescription(Errors errors, String description) {
    String trimmed = description == null ? "" : description.trim();
    if (trimmed.length() > 500) {
      errors.addFieldError("description", "[tooLong]description",
          "The description is [%d] characters long. Descriptions must be 500 characters or fewer.",
          trimmed.length());
    }
    // Empty/blank is valid (optional field). No other checks.
  }
```

Then wire the shared helper into the existing `validate(Group group)` so create enforces the same rule. Immediately after the existing null guard:

```java
    if (group == null) {
      errors.addGeneralError("[null]group", "A group is required.");
      return errors;
    }
```

add this line (so the description is checked even on the blank-name early return):

```java
    validateDescription(errors, group.description());
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: PASS — including the pre-existing `GroupValidatorTest` methods (unchanged behavior for valid descriptions) and the 5 new methods.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/validation/GroupValidator.java src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java
git commit -m "feat(validation): shared description length check reused by create and update

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `DatabaseClient.updateGroupDescription`

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Test: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DatabaseClientTest` (it already constructs a `DatabaseClient client` against the real D1 — mirror the surrounding tests' setup/teardown style; if existing tests use a fixture group prefix, follow it). Use a unique short-name group so no TLD/verification machinery is involved:

```java
  @Test
  public void updateGroupDescription_persistsNewValue() {
    String name = "dbtest-desc-" + java.util.UUID.randomUUID();
    client.insertGroup(new Group(name, "old", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      client.updateGroupDescription(name, "new description");
      Group reloaded = client.findGroup(name).orElseThrow();
      assertEquals(reloaded.description(), "new description");
      assertEquals(reloaded.name(), name);
      assertEquals(reloaded.state(), GroupState.VERIFIED);
    } finally {
      client.deleteGroup(name);
    }
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: compilation failure — `updateGroupDescription` does not exist.

- [ ] **Step 3: Implement the method**

In `DatabaseClient.java`, add this method next to `updateGroupState` (keep the existing alphabetical-by-name ordering of the `update*` methods — place it before `updateGroupState`):

```java
  public void updateGroupDescription(String name, String description) {
    query(
        "UPDATE groups SET description = ? WHERE name = ?",
        description,
        name
    );
  }
```

(`query(String sql, Object... args)` is the same internal helper `updateGroupState` uses.)

- [ ] **Step 4: Run test to verify it passes**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: PASS — `updateGroupDescription_persistsNewValue` green, no regressions in the class.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): add updateGroupDescription

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `GroupService.updateDescription`

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/GroupService.java`
- Test: `src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `GroupServiceTest` (it already wires `service`, `client`, `validator` in `@BeforeClass`; `input(name, description)` helper and `import static org.testng.Assert.*;` exist):

```java
  @Test
  public void updateDescription_persistsTrimmedValue() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999999"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.updatedesc", "original"), creator);
    try {
      service.updateDescription(g, "  updated value  ");
      assertEquals(client.findGroup("io.github.updatedesc").orElseThrow().description(), "updated value");
    } finally {
      client.deleteGroup("io.github.updatedesc");
    }
  }

  @Test
  public void updateDescription_allowsEmpty() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999998"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.emptydesc", "had a description"), creator);
    try {
      service.updateDescription(g, "");
      assertEquals(client.findGroup("io.github.emptydesc").orElseThrow().description(), "");
    } finally {
      client.deleteGroup("io.github.emptydesc");
    }
  }

  @Test
  public void updateDescription_rejectsTooLong() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999997"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.toolong", "original"), creator);
    try {
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.updateDescription(g, "x".repeat(501))
      );
      assertNotNull(ex.errors().getFieldError("description", "[tooLong]description"));
      // Nothing persisted.
      assertEquals(client.findGroup("io.github.toolong").orElseThrow().description(), "original");
    } finally {
      client.deleteGroup("io.github.toolong");
    }
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: compilation failure — `service.updateDescription` does not exist.

- [ ] **Step 3: Implement the service method**

In `GroupService.java`, add this method after `delete(...)` and before `findGroup(...)` (keeps rough alphabetical method ordering: create, delete, updateDescription is fine here — match the file's existing ordering; if it is strictly alphabetical, place it after `listForUser`):

```java
  /**
   * Updates the description of {@code group}. The description is optional and capped at 500
   * characters (validated via {@link GroupValidator#validateUpdateDescription(String)}); it is
   * trimmed before persisting, matching the {@link Group} record's normalization.
   *
   * @param group       The group to update. Only its name is used to target the row.
   * @param description  The new description. May be {@code null} or blank (cleared to "").
   * @throws ValidationException If the description exceeds the length limit.
   */
  public void updateDescription(Group group, String description) {
    Errors errors = validator.validateUpdateDescription(description);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    String normalized = description == null ? "" : description.trim();
    databaseClient.updateGroupDescription(group.name(), normalized);
  }
```

`Errors`, `ValidationException`, `Group` resolve through the file's existing `import module org.lattejava.app;` and `import org.lattejava.app.service.validation.*;`. The fields `validator` and `databaseClient` already exist on `GroupService`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: PASS — 3 new methods green, existing `GroupServiceTest` methods unaffected.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/GroupService.java src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java
git commit -m "feat(service): GroupService.updateDescription

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Templates surface the description error

**Files:**
- Modify: `web/pages/groups/settings.jte`
- Modify: `web/pages/groups/detail.jte`

No new unit test — JTE compiles all templates at server boot (`@BeforeSuite` in `MainTest`), so a malformed template fails the suite. The behavior is verified end-to-end in Task 6.

- [ ] **Step 1: Add the `errors` param and field error to `settings.jte`**

In `web/pages/groups/settings.jte`, add an import and param at the top (after the existing `@import`/`@param` lines, mirroring `members.jte`):

Change the header from:

```
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@param Group group
```

to:

```
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import org.lattejava.app.error.Errors
@param Group group
@param Errors errors = new Errors()

!{java.util.List<org.lattejava.app.error.Error> descErrors = errors.fieldErrors.getOrDefault("description", java.util.List.of());}
!{String descError = descErrors.isEmpty() ? null : descErrors.get(0).message;}
```

Then change the description input call from:

```
@template.components.input(name="description", label="Description", value=group.description() != null ? group.description() : "", hint="Shown publicly on artifact pages.")
```

to (the `input` component already renders `error` in red and suppresses the hint when an error is present):

```
@template.components.input(name="description", label="Description", value=group.description() != null ? group.description() : "", hint="Shown publicly on artifact pages.", error=descError)
```

The default `errors = new Errors()` keeps the existing `GET /app/groups/{name}/settings` render working unchanged (no errors → `descError` is null → hint shows as before).

- [ ] **Step 2: Thread `errors` through `detail.jte`**

In `web/pages/groups/detail.jte`, the settings branch currently reads:

```
  @elseif(activeTab.equals("settings"))
    @template.pages.groups.settings(group = group)
```

Change it to pass the errors param (`detail.jte` already declares `@param Errors errors = new Errors()`):

```
  @elseif(activeTab.equals("settings"))
    @template.pages.groups.settings(group = group, errors = errors)
```

- [ ] **Step 3: Verify templates compile**

Run: `latte build`
Expected: BUILD SUCCESSFUL (JTE precompilation passes; no template syntax errors).

- [ ] **Step 4: Commit**

```bash
git add web/pages/groups/settings.jte web/pages/groups/detail.jte
git commit -m "feat(ui): surface description validation error on settings tab

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Controller handler + route

**Files:**
- Modify: `src/main/java/org/lattejava/app/controller/GroupController.java`
- Modify: `src/main/java/org/lattejava/app/Main.java:103`

- [ ] **Step 1: Add the `updateSettings` handler**

In `GroupController.java`, add this method directly after the existing `settings(...)` method (so the GET renderer and POST handler sit together):

```java
  public void updateSettings(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    Group group = findGroup(req);
    String description = req.getParameter("description");
    try {
      groupService.updateDescription(group, description);
      res.sendRedirect("/app/groups/" + group.name() + "/", 303);
    } catch (ValidationException e) {
      MainView view = viewService.buildMainView(user);
      templates.html("pages/groups/detail.jte", req, res,
          Map.of(
              "activeTab", "settings",
              "group", group,
              "view", view,
              "errors", e.errors()
          )
      );
    }
  }
```

`findGroup(req)` (already in this class) throws `MissingException` → framework 404 when the group does not exist. `HTTPRequest`, `HTTPResponse`, `Map`, `IOException`, `MainView`, `ValidationException`, `Group`, `User` all resolve through the file's existing `import module` declarations (same imports `settings`/`create` already use). No permission check — deferred to the permissions-middleware feature.

- [ ] **Step 2: Register the route**

In `Main.java`, the group routes block currently ends like this (around line 95-103):

```java
              groupsRoute.get("/", groups::list)
                         .get("/new", groups::newForm)
                         .post("/new", groups::create)
                         .get("/{groupName}/", groups::detail)
                         .get("/{groupName}/settings", groups::settings)
                         .get("/{groupName}/verify", groups::verifyForm)
                         .post("/{groupName}/delete", groups::delete)
                         .post("/{groupName}/verify/check", groups::checkVerification)
                         .post("/{groupName}/verify/github", groups::verifyGitHub);
```

Add the new POST route immediately after the `.get("/{groupName}/settings", groups::settings)` line so the GET and POST for settings are adjacent:

```java
              groupsRoute.get("/", groups::list)
                         .get("/new", groups::newForm)
                         .post("/new", groups::create)
                         .get("/{groupName}/", groups::detail)
                         .get("/{groupName}/settings", groups::settings)
                         .post("/{groupName}/settings", groups::updateSettings)
                         .get("/{groupName}/verify", groups::verifyForm)
                         .post("/{groupName}/delete", groups::delete)
                         .post("/{groupName}/verify/check", groups::checkVerification)
                         .post("/{groupName}/verify/github", groups::verifyGitHub);
```

(No trailing slash — this is a POST endpoint, per `web-conventions.md`.)

- [ ] **Step 3: Verify it compiles**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/controller/GroupController.java src/main/java/org/lattejava/app/Main.java
git commit -m "feat(controller): POST /app/groups/{name}/settings updates description

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: End-to-end HTTP test

**Files:**
- Test: `src/test/java/org/lattejava/app/tests/MainTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `MainTest` (it already has `WebTest test`, `OIDCTestFixture oidc`, and uses `StringBodyAsserter`; `ResetItem` is already imported and used by `createGroup`). These mirror the existing `createGroup` test: create a fresh group over HTTP, then update its description.

```java
  @Test
  public void updateSettings_redirectsOnSuccess() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    String name = "settings-ok-" + UUID.randomUUID();
    test.withForm(Map.of("name", name))
        .post("/app/groups/new")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .withForm(Map.of("description", "  a new description  "))
        .post("/app/groups/" + name + "/settings")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .get("/app/groups/" + name + "/settings")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("a new description"));
  }

  @Test
  public void updateSettings_rerendersWithErrorWhenTooLong() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    String name = "settings-toolong-" + UUID.randomUUID();
    test.withForm(Map.of("name", name))
        .post("/app/groups/new")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .withForm(Map.of("description", "x".repeat(501)))
        .post("/app/groups/" + name + "/settings")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("Descriptions must be 500 characters or fewer"));
  }
```

These groups use plain short names (`settings-ok-<uuid>`), which create as `VERIFIED` short-name groups (no DNS/GitHub verification), keeping the test focused on the settings flow. They are left in the DB like other `MainTest` create-style tests; `resetAndSeedDatabase` wipes groups between suite runs.

- [ ] **Step 2: Run tests to verify they fail**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: Before Task 1-5 are merged this would fail; at this point in the plan Tasks 1-5 are done, so run it to confirm the new tests pass. If you are executing strictly TDD and Tasks 1-5 are complete, this step instead confirms GREEN — note the expected outcome and proceed to Step 4. (The genuine red→green cycle for the underlying logic happened in Tasks 1 and 3.)

- [ ] **Step 3: (Not applicable — implementation already complete in Tasks 1-5)**

No code to write here; the handler, route, service, validator, DB method, and templates were implemented and individually tested in Tasks 1-5. This task only adds the black-box HTTP coverage.

- [ ] **Step 4: Run tests to verify they pass**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: PASS — `updateSettings_redirectsOnSuccess` and `updateSettings_rerendersWithErrorWhenTooLong` green, no regressions in `MainTest`.

- [ ] **Step 5: Run the full suite**

Run: `latte test`
Expected: BUILD SUCCESSFUL — entire TestNG suite green (GroupValidatorTest, DatabaseClientTest, GroupServiceTest, MainTest, and all others).

- [ ] **Step 6: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "test(main): end-to-end coverage for group settings update

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Self-Review (completed by plan author)

**Spec coverage:**

| Spec section | Task |
|---|---|
| Route `POST /{groupName}/settings` | Task 5 (Step 2) |
| Controller `updateSettings` (success redirect to `/app/groups/{name}/`, re-render on `ValidationException`) | Task 5 (Step 1) |
| `GroupService.updateDescription` (validate, normalize/trim, persist) | Task 3 |
| Shared `validateDescription` helper reused by `validateUpdateDescription` AND `validate(Group)` (create) | Task 1 |
| `DatabaseClient.updateGroupDescription` | Task 2 |
| `settings.jte` gains `errors` param + field error; `detail.jte` threads it | Task 4 |
| 404 when group missing | Task 5 (Step 1, via `findGroup`/`MissingException`) |
| No permission checks (deferred) | Explicit in Task 5 Step 1 + plan header |
| Tests: update success+redirect, trim, empty allowed, too-long re-render, name/state/createdAt untouched, shared check on create | Tasks 1, 2, 3, 6 |

All spec requirements map to a task. No gaps.

**Placeholder scan:** No TBD/TODO; every code step shows complete code; every command shows expected output. (Task 6 Steps 2-3 intentionally document the TDD ordering rather than a fake red state — the real red→green cycles are in Tasks 1 and 3.)

**Type consistency:** `validateUpdateDescription(String)`, `validateDescription(Errors, String)`, `updateDescription(Group, String)`, `updateGroupDescription(String, String)`, error code `[tooLong]description`, field `description`, redirect target `/app/groups/{name}/` — used identically across Tasks 1-6 and matched against the actual `Errors`/`DatabaseClient`/`GroupController` APIs in the codebase.
