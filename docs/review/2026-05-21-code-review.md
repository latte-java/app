# Code Review — `features/members` branch

**Status: COMPLETE (2026-05-21)** — all 15 findings triaged. Outcome: 11 resolved, 2 marked invalid (#2 PENDING-leave is intentional; #4 username-nullable was incorrect), 2 skipped at owner request (#13, #14), 1 paired with another item and dropped (#8 → #2).

Branch HEAD at review time: `94a3c809bf139508caae252033d4a6566909d01f`
Merge base with `main`: `2d9d9c31a2675c1d147ec2c781e41cd350220bb5`
Scope: 62 files changed, ~3,300 net additions across the member-identity + invite-page work.

Findings are grouped by severity. Each item lists the location, what was wrong, and the resolution.

---

## Security / correctness bugs

### 1. IDOR on `POST /{userId}/accept` and `POST /{userId}/decline` — RESOLVED

`src/main/java/org/lattejava/app/controller/MembershipController.java:31-36` (accept) and `:57-62` (decline) take `userId` from the URL path and pass it straight to `acceptInvitation` / `declineInvitation`, with no check that the URL `userId` matches `oidc.user().userId()`.

The routes in `src/main/java/org/lattejava/app/Main.java:117-118` intentionally drop the `isOwner` middleware so a PENDING invitee can act on their own row. But `GroupSecurity` only requires *some* membership row in the group (PENDING or ACTIVE), so any member of group G can POST `/app/groups/G/members/{otherUUID}/accept` to flip another user's PENDING row to ACTIVE, or `.../decline` to delete it.

**Resolution:** dropped `{userId}` from both routes entirely. They are now `POST /accept` and `POST /decline`, and the handlers act on `oidc.user().userId()` directly — there is no way to address another member's row through these endpoints. The overview-page Accept/Decline forms were updated, and `AcceptDeclineFlowTest` was rewritten against `testUserId`.

### 2. PENDING invitees can `POST /leave` and delete their row — INTENTIONAL (working as designed)

Original concern: `Main.java:123-124` registers `/leave` with no per-route role middleware. `GroupSecurity` lets PENDING rows through, and `MembershipValidator.validateLeave` only enforces last-owner protection, not state — so a PENDING invitee can POST `/leave` and have their row deleted.

**Resolution:** this is working as designed. There is no security risk in letting a PENDING invitee leave — functionally they are deleting their own invitation row, which is the same outcome as `decline`. No data belonging to other users is touched. Locked in with `MembershipServiceTest.leave_pendingInvitee_deletesRow`, which asserts the row is deleted with no last-owner check. The stale "leave reaches it for ACTIVE members" comment in `Main.java:109-112` is the only loose end and can be cleaned up alongside other doc/comment fixes.

### 3. `ViewService.buildGroupView` uses bare `orElseThrow()` — RESOLVED

`src/main/java/org/lattejava/app/service/ViewService.java:30` previously called `findMember(...).orElseThrow()` with no message, producing a contextless `NoSuchElementException` and an ugly 500.

**Resolution:**

- `buildGroupView` now throws `IllegalStateException` with the bracket-formatted message `"No membership row for user [...] in group [...]"`, satisfying `.claude/rules/error-messages.md`.
- Added `org.lattejava.app.middleware.AppExceptionHandler`, an `ExceptionHandler` subclass that maps `Exception → 500` and renders the new `web/pages/error.jte` page.
- Installed `AppExceptionHandler` first in the middleware chain in `Main.java` so it wraps every other middleware and handler.
- `error.jte` mirrors the `404.jte` aesthetic (coffee-themed fake stack trace, dashboard/groups back-buttons, fun footer).
- Exported `org.lattejava.app.middleware` from `module-info.java`.

### 4. `User.username` is nullable but rendered unguarded — INVALID

Original concern: `User.username` is documented as null for not-yet-registered/invited users, so the unguarded `${view.viewer().username()}` renders in sidebar/dashboard/member-row were a NPE/`"null"`-string risk.

**Resolution:** username is not nullable in practice — the kickstart and FA flow guarantee a username for every authenticated user, and invited-only rows are enriched through a path that doesn't hit these viewer-render sites. No code change needed.

---

## UX bugs

### 5. `POST /delete` redirects to `/settings` on ValidationException — RESOLVED

`GroupController.delete` previously redirected to `/app/groups/{name}/settings` on `ValidationException`, bouncing the user out of the delete flow.

**Resolution:**

- `delete.jte` now takes `Errors errors = new Errors()` and renders general errors at the top in the same style as `remove.jte`.
- `GroupController.delete` and `deleteForm` now share a private `renderDeleteForm(req, res, user, group, errors)` helper (matching the `renderInviteForm` / `renderRemoveForm` / `renderRoleForm` pattern in `MembershipController`). On `ValidationException`, the helper re-renders the delete page with the errors instead of redirecting.
- Coverage: the service-level validation path is exercised by `GroupServiceTest.delete_bucketNotEmpty_throws` (with a fake `R2Client`); HTTP-level coverage of the re-render path requires injecting a fake R2 into the running `Main`, which isn't supported by the current `Services` wiring, so left to manual verification.

---

## Test issues

### 6. `GroupSecurityWiringTest` lists `GET /members/` in both `memberOnly` and `ownerOnly` providers — RESOLVED

Removed `{"GET", "/members/"}` from the `memberOnly` data provider in `GroupSecurityWiringTest`. The route is owner-only and is correctly listed in `ownerOnly`; the duplicate entry was leftover from before commit `cda4d0c` added `isOwner` to the route.

### 7. `GroupSecurityTest` class Javadoc contradicts its own assertions — RESOLVED

The Javadoc claimed PENDING members get 200 on `/members/`, but the test asserted 303. Root cause: the tests were using `/members/` (owner-only) to test `GroupSecurity`'s base membership check, so the 303 actually came from the `isOwner` gate, not from `GroupSecurity`.

**Resolution:** switched all `isMember_*` tests to use `GET /{groupName}/` (the detail route — gated only by `GroupSecurity`'s base membership check, no role middleware). `isMember_pendingMember_redirectsHome` is renamed `isMember_pendingMember_passes` and now correctly asserts 200, matching the documented PENDING-can-view-group behavior. Javadoc updated to match.

### 8. No PENDING-member coverage for `GET /members/leave` or `POST /members/leave` — SKIPPED

Pairs with finding 2. Since #2 is intentional (PENDING leaving is allowed and equivalent to decline), the proposed `pendingMember_redirectsHome` test case would assert the wrong behavior. PENDING-leave is locked in by `MembershipServiceTest.leave_pendingInvitee_deletesRow` (added under #2), so no additional coverage is needed here.

### 9. `MembershipServiceTest.listMembersEnrichesUserFromFusionAuth` has no `finally` cleanup and doesn't assert username — RESOLVED

Wrapped the assertion block in `try { ... } finally { client.deleteGroup("test.enrich.fixture") }` to match every other test in the file. Added `assertEquals(members.getFirst().user().username(), "OrdinaryUser")` (the kickstart-provisioned username) so a regression that stops populating the username field would fail the test.

---

## Documentation drift

### 10. Invite docs say 404 for missing group; code returns 303 — RESOLVED

Updated the design doc and implementation plan to reflect the actual behavior: `GroupSecurity` (installed at the `/app/groups` prefix) 303-redirects missing-group requests to `/app/` before the controller runs, so the controller's empty-group check is defensive dead-code in practice. Renamed the documented test from `inviteForm_missingGroup_returns404` to `inviteForm_missingGroup_redirectsHome`. The committed code snippets retain the defensive `setStatus(404)` (left untouched — it documents the historical implementation step).

---

## Performance

### 11. `GroupSecurity` + `HasRole` double-fetch the same group and member — RESOLVED

`GroupSecurity` now stashes the resolved `Group` and `Member` as request attributes (`GroupSecurity.GROUP_ATTRIBUTE` and `GroupSecurity.MEMBER_ATTRIBUTE`) after a successful pass. `HasRole` reads the cached `Member` directly — no second `groupService.findGroup` or `membershipService.findMember` call, no `groupService` dependency, no TOCTOU window between the membership-presence check and the role check.

`HasRole` throws `IllegalStateException` (caught by `AppExceptionHandler` → error page) if the cached `Member` attribute is missing, enforcing the documented contract that `GroupSecurity` must be installed upstream. Verified by running `GroupSecurityTest` (8/8 pass) and `GroupSecurityWiringTest` (52/52 pass).

### 12. `renderRemoveForm` / `renderRoleForm` use `listMembers` to find one member — RESOLVED

Added `MembershipService.findEnrichedMember(groupName, userId)` — a single-row D1 lookup followed by a single FA `retrieveUser(userId)` call to populate `Member.user()`. Falls back to the unenriched DB user (with a WARNING log) when FA has no matching record, matching the existing `listMembers` behavior.

Updated `renderRemoveForm` and `renderRoleForm` to call `findEnrichedMember` instead of streaming over the full enriched member list. The Form helpers now do 1 D1 lookup + 1 FA call per render instead of 1 D1 list + 1 FA batch search — a meaningful saving on every `ValidationException` re-render. Verified by `RemoveFlowTest` (5/5 pass) and `RoleFlowTest` (8/8 pass).

---

## Conventions

### 13. `MainView` fields not alphabetized after rename — SKIPPED

### 14. Class imports added where `import module` already covers them — SKIPPED

### 15. JTE component calls use spaces around `=` — RESOLVED

Stripped spaces around `=` on the four diff-touched non-compact lines:

- `web/components/member-row.jte` — the `avatar(email=..., size=32)` call (pre-existing `icon(name = "clock", ...)` and `badge(label = ..., tone = ...)` calls are context lines, not diff-touched, so left untouched per the "don't fix things not in the diff" review convention)
- `web/components/radio-card.jte` — `icon(name=icon, size=18)`
- `web/pages/groups/settings.jte` — `admonition(... cssClass="mt-4")`
- `web/pages/groups/verify.jte` — `layout.main(view=..., pageTitle=..., activeNav=..., activeGroupId=..., content=@\`...)`

Verified: `SettingsViewTest` (7/7), `InviteFlowTest` (5/5), `VerificationFlowTest` (5/5) all pass.

---

## Outcome summary

| # | Item | Outcome |
|---|------|---------|
| 1 | IDOR on accept/decline | RESOLVED — `{userId}` removed from routes; handlers use authenticated user |
| 2 | PENDING `/leave` deletes row | INTENTIONAL — locked in by `leave_pendingInvitee_deletesRow` |
| 3 | Bare `orElseThrow()` in `buildGroupView` | RESOLVED — bracketed message + new `AppExceptionHandler` rendering `pages/error.jte` |
| 4 | `User.username` nullable | INVALID — username is non-null in practice |
| 5 | `POST /delete` redirects to `/settings` on error | RESOLVED — re-renders delete page with errors via `renderDeleteForm` |
| 6 | `GET /members/` in both `memberOnly` and `ownerOnly` providers | RESOLVED — removed from `memberOnly` |
| 7 | `GroupSecurityTest` Javadoc contradicts assertions | RESOLVED — switched tests to `GET /{groupName}/`; PENDING now correctly asserts 200 |
| 8 | No PENDING coverage for `/leave` | SKIPPED — paired with #2 |
| 9 | Enrich test had no cleanup or username assertion | RESOLVED — added try/finally + username assertion |
| 10 | Invite docs say 404 for missing group | RESOLVED — docs updated to 303 redirect via `GroupSecurity` |
| 11 | `GroupSecurity` + `HasRole` double-fetch | RESOLVED — cached `Group`/`Member` as request attributes; `HasRole` reads cache |
| 12 | `renderRemoveForm` / `renderRoleForm` use `listMembers` | RESOLVED — new `findEnrichedMember` single-row + single-FA call |
| 13 | `MainView` fields not alphabetized | SKIPPED |
| 14 | Class imports vs module imports | SKIPPED |
| 15 | JTE spaces around `=` | RESOLVED — fixed four diff-touched lines |
