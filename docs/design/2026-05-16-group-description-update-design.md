# Group Description Update — Design

**Date:** 2026-05-16
**Status:** Approved

## Problem

The group settings page (`web/pages/groups/settings.jte`) renders a form that
posts a `description` field to `POST /app/groups/{name}/settings`, but no such
route or handler exists — only the `GET`. Submitting the form currently does
nothing. This feature adds the functionality to update a group's description.

## Scope

- **In scope:** Updating a group's `description` only. The Group ID (name) is
  permanent and is displayed read-only in the form.
- **Out of scope — explicitly deferred:**
  - **Permission enforcement.** Who may edit a group is being handled by a
    dedicated permissions middleware in a separate feature. This feature
    performs **no** OWNER/role/membership checks.
  - Domain verification and the danger-zone actions (leave/delete) already have
    their own handlers and are untouched.

## Decisions

| Decision        | Choice                                                              |
|-----------------|---------------------------------------------------------------------|
| Editable fields | `description` only                                                  |
| Permissions     | None in this feature (deferred to permissions middleware)           |
| Description     | Optional (empty allowed); trimmed; max **500** characters           |
| Success UX      | `303` redirect to the group detail page `/app/groups/{name}/`       |
| Validation UX   | Re-render the settings tab inline with the field error              |

Rationale for 500: the description is shown publicly on artifact pages, so a
guardrail is warranted, but it is not a required field.

## Architecture

Follows the project's controller → service → validation layering
(`.claude/rules/web-conventions.md`). Validation lives in `GroupValidator`,
not in the controller or service.

### 1. Route

`Main.java`, in the existing `/groups` prefix block, alongside the other group
routes:

```java
.post("/{groupName}/settings", groups::updateSettings)
```

### 2. Controller — `GroupController.updateSettings(HTTPRequest, HTTPResponse)`

1. `User user = oidc.user();`
2. `Group group = findGroup(req);` — reuses the existing helper, which throws
   `MissingException` (→ 404) when the group does not exist.
3. Read `String description = req.getParameter("description");`
4. `try`:
   - `groupService.updateDescription(group, description);`
   - `res.sendRedirect("/app/groups/" + group.name() + "/", 303);`
5. `catch (ValidationException e)`:
   - `MainView view = viewService.buildMainView(user);`
   - Re-render `pages/groups/detail.jte` with
     `{"activeTab": "settings", "group": group, "view": view, "errors": e.errors()}`
     (mirrors how `create` re-renders `new.jte` on validation failure).

No permission branching, no redirect-on-permission path — there are no
permission checks in this feature.

### 3. Service — `GroupService.updateDescription(Group group, String description)`

```java
public void updateDescription(Group group, String description) {
  Errors errors = validator.validateUpdateDescription(description);
  if (!errors.empty()) {
    throw new ValidationException(errors);
  }
  String normalized = description == null ? "" : description.trim();
  databaseClient.updateGroupDescription(group.name(), normalized);
}
```

Normalization matches what the `Group` record's compact constructor already
applies to `description` (`trim()`, default `""`), keeping the persisted value
consistent regardless of entry path.

### 4. Validation — shared description check, reused everywhere

The 500-character rule is the single source of truth for "is this description
valid" and **must be reused anywhere a description is validated** — both the
new update path and the existing create path. It is factored into one private
helper on `GroupValidator`:

```java
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

Two callers:

1. **`GroupValidator.validateUpdateDescription(String description)`** (new) —
   creates an `Errors`, calls `validateDescription(errors, description)`,
   returns it. Kept as a distinct public method (rather than reusing
   `validate(Group)`) because `validate` performs a duplicate-name check that
   would falsely fail for an already-existing group being edited.

2. **`GroupValidator.validate(Group group)`** (existing, used by
   `GroupService.create`) — add a call to
   `validateDescription(errors, group.description())` so a too-long
   description is rejected at create time too. This closes the current gap
   where create does no description-length check at all, and guarantees the
   rule cannot diverge between the two paths.

There is exactly one place that defines the length rule; both entry points go
through it.

### 5. Persistence — `DatabaseClient.updateGroupDescription(String name, String description)`

Mirrors the existing `updateGroupState`:

```java
public void updateGroupDescription(String name, String description) {
  execute("UPDATE groups SET description = ? WHERE name = ?", description, name);
}
```

(The exact internal helper — `execute`/`query` — will match what
`updateGroupState` uses in `DatabaseClient`.)

### 6. Templates

- `web/pages/groups/settings.jte`: add `@param org.lattejava.app.error.Errors
  errors = new Errors()` (default so existing GET render keeps working) and
  render the `description` field error within the General card, reusing the
  error-display style already used by the members tab for visual consistency.
- `web/pages/groups/detail.jte`: in the
  `@elseif(activeTab.equals("settings"))` branch, pass `errors = errors`
  through to the settings template (it already threads `errors` to the members
  tab).

## Data Flow

```
POST /app/groups/{name}/settings  (description=...)
  → GroupController.updateSettings
      → findGroup(req)                     (404 if missing)
      → GroupService.updateDescription
          → GroupValidator.validateUpdateDescription   (length only)
              ↳ errors → throw ValidationException
          → DatabaseClient.updateGroupDescription      (UPDATE groups SET description)
      → success: 303 → /app/groups/{name}/
      → ValidationException: re-render detail.jte (settings tab) with errors
```

## Error Handling

- **Group not found:** `findGroup` throws `MissingException` → framework 404.
  No change needed.
- **Description too long:** `ValidationException` with a `description` field
  error → settings tab re-rendered inline, user keeps their input, nothing
  persisted.
- **Permissions:** not handled here by design — deferred to the permissions
  middleware feature.

## Testing

TestNG, booting a real `Main` and driving the running server with `WebTest` +
`OIDCTestFixture` (no mocks), following the existing group tests:

1. Update a group's description → response is `303` to `/app/groups/{name}/`;
   re-fetching the group shows the new description.
2. Description with surrounding whitespace → persisted value is trimmed.
3. Empty description → allowed; persists as `""`.
4. Description longer than 500 characters → settings tab re-rendered
   containing the `[tooLong]description` error; the stored description is
   unchanged.
5. `name`, `state`, and `createdAt` are unaffected by the update.
6. **Shared check on create:** creating a group with a >500-character
   description is rejected with the same `[tooLong]description` error
   (proves `validate(Group)` and `validateUpdateDescription` share the rule).

## Module / Convention Notes

- New controller method is `public` (referenced from `Main`).
- Route registered inside the existing `/groups` prefix block per
  `web-conventions.md` (no trailing slash — this is a POST endpoint).
- No new external dependencies → no `project.latte` / `module-info.java`
  changes expected.
