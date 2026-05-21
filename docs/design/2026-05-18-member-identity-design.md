# Member Identity from FusionAuth — Design

**Date:** 2026-05-18
**Status:** Approved (pending written-spec review)

## Problem

The members list page renders the raw FusionAuth user UUID for each member —
as the displayed name, as the avatar key, and (incidentally) it is the only
identity the `Member` record carries. Users need to see who a member *is*: their
email address and their FusionAuth username. FusionAuth is the system of record
for user identity; the D1 `members` table stores only the user UUID.

## Goals

- The members list shows each member's **email** and **username** instead of the
  UUID.
- Identity is sourced from FusionAuth (system of record), resolved in a single
  batched call per page render.
- Templates reference identity through `Member.user` — no identity logic in the
  view layer.
- `User` matches FusionAuth's identity shape.

## Non-goals

- No D1 schema change / migration.
- No route changes.
- No denormalization of email/username into the `members` table.
- No unrelated refactoring.

## Identity facts (decided during brainstorming)

- "Username" means the **FusionAuth `username` field** (the GitHub `login` for
  GitHub-linked users, per the kickstart's `usernameClaim=login`).
- FusionAuth forces every registered user to set a unique username at account
  creation → for any **registered** user, `username` is non-null.
- An **invited (PENDING)** user is created by `MembershipService.invite` with
  only `id` + `email` set, so `username` is null until they register. Email is
  always set.
- The signed-in viewer is, by definition, a registered user → their `username`
  is always non-null.
- Null `username` therefore occurs **only** for PENDING members. This is the
  single case requiring a guard, and it coincides exactly with the existing
  `MembershipState.PENDING` branch in `member-row.jte`.

## Design

### `User` (`org.lattejava.app.model.User`)

```java
public record User(
    UUID userId,     // FusionAuth user UUID (sub claim)
    String email,    // primary identifier
    String username  // FusionAuth username; null only for not-yet-registered (invited) users
) {}
```

`name` is removed.

### `UserService` (`org.lattejava.app.service.UserService`)

- `toUser(JWT)`: `userId` from `sub`, `email` from `email`, `username` from the
  `preferred_username` claim. The previous `name` / `given_name` /
  `family_name` / email-fallback derivation block is removed (registration does
  not collect given/family name).
- Add `toUser(io.fusionauth.domain.User faUser)` →
  `new User(faUser.id, faUser.email, faUser.username)`. Used to enrich members
  and invited users from FusionAuth responses.

> Implementation check: confirm the JWT carries the FA username under
> `preferred_username` against a real token from the local FusionAuth. The OIDC
> standard claim is `preferred_username`; adjust the claim name here only if the
> actual token differs. This is the one assumption to verify in code, not a
> reason to add fallbacks.

### `Member` (`org.lattejava.app.model.Member`)

```java
public record Member(
    String groupName,
    User user,
    Role role,
    MembershipState state,
    UUID invitedBy,
    Instant invitedAt,
    Instant joinedAt
) {}
```

The separate `userId` field is removed — the id lives in
`member.user().userId()`, the single source of truth. `invitedBy` stays a
`UUID` (no identity rendered for it).

### `DatabaseClient`

- `listMembers` / `findMember`: build each `Member` with
  `user = new User(<uuid from DB>, null, null)` (id-only carrier; the DB layer
  has no FusionAuth knowledge).
- `insertMember`: read the id via `member.user().userId()`. No schema change.

### `MembershipService`

- `listMembers(String groupName)`: fetch members from D1, collect ids via
  `m.user().userId()`, issue **one** `fusionAuth.searchUsersByIds(ids)` call,
  then rebuild each `Member` with `UserService.toUser(faUser)` as `user` (other
  fields copied). This is the only path that enriches identity.
- `invite(...)`:
  - Existing-FA-user branch: build `Member.user` from the lookup result via
    `UserService.toUser(lookup.successResponse.user)` (email + username — an
    existing registered user has a username).
  - Newly-created-user branch: `new User(userId, email, null)` (email always
    set; username null until they register).
- `findMember`, `acceptInvitation`, `declineInvitation`, and the validation
  paths remain DB-only and operate on the id-only `User`. Any internal
  `member.userId()` usage becomes `member.user().userId()`.

### Templates

- **`web/components/member-row.jte`**: param stays `Member member`.
  - Primary line: `member.user().email()` (always set, both branches).
  - Avatar: keyed by `member.user().email()` — this also fixes the current bug
    where the avatar is keyed by the UUID.
  - Secondary line: `member.user().username()`, rendered **only in the
    non-pending (`@else`) branch**, where registration guarantees it non-null.
    PENDING rows keep the existing clock icon / `invited` badge /
    "Awaiting acceptance" UI and show email only.
  - Form action URLs (`/role`, `/remove`): `member.user().userId()`.
- **`web/layout/sidebar.jte:36`**: `view.viewer().name()` →
  `view.viewer().username()`.
- **`web/pages/dashboard.jte:12`**: replace the
  `view.viewer().name().split(" ")[0]` greeting with
  `view.viewer().username()` (no space-split). No guard — the viewer is always
  registered.

### Controller

`MembershipController` is unchanged in behavior: it parses the `{userId}` route
param directly into a `UUID` (it does not go through `Member`), and
`renderMembers` already calls `membershipService.listMembers(groupName)`, which
now returns identity-enriched members.

## Compilation fallout (enumerate during planning)

Signature changes to `User` and `Member` touch every construction/accessor
site:

- `new User(...)` — `UserService`, test fixtures, any test asserting on `name`.
- `new Member(...)` — `MembershipService.invite`, `DatabaseClient`, tests.
- `User.name()` callers — `sidebar.jte`, `dashboard.jte` (Java side: none found
  so far; confirm in planning).
- `member.userId()` callers — `MembershipValidator`, `MembershipService`,
  `DatabaseClient.insertMember` → `member.user().userId()`.

## Testing

- Extend the existing membership-list test to assert the rendered HTML contains
  the FA test user's email and does **not** contain the raw UUID.
- Fix any `UserService` / OIDC test that asserts on the removed `name` field.
- Full `latte test` (requires local FusionAuth on `:9011` and D1 network
  access, per CLAUDE.md).

## Risks

- **`preferred_username` claim assumption** — mitigated by the implementation
  check above; verified against a real token before relying on it.
- **`searchUsersByIds` returning fewer users than requested** — only possible if
  a `members` row references a UUID with no FusionAuth user, which the
  invite/registration flow does not produce. Not guarded (per decision); a miss
  would surface as a clear error during the rebuild rather than silent bad data.
