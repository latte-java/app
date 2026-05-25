# Invite Member — Full Page Form

**Date:** 2026-05-19
**Status:** Approved

## Problem

The "Invite member" button on the group members tab (`web/pages/groups/members.jte`)
links to `?invite=1#invite`, which reveals an inline form rendered conditionally on the
same members tab. The original plan to use a dialog was dropped. The button does not
provide a working, dedicated flow.

## Goal

Replace the inline invite form with a dedicated full-page invite form that mirrors the
"New group" page (`web/pages/groups/new.jte`) in structure and styling, including
explanatory cards for the available roles. The role cards double as the role selector
(clickable radio-style cards).

## Scope

In scope:

- A new GET page for the invite form.
- A new generic `radio-card` JTE component.
- Reworking the "Invite member" button to navigate to the new page.
- Moving invite validation-error rendering to the new page.
- Removing the now-dead inline invite form and its parameter plumbing.

Out of scope:

- Changes to `MembershipService.invite()` logic, the `InviteRequest` model, or
  validation rules.
- Changes to the invite email or FusionAuth flow.
- The success redirect target (unchanged: `/app/groups/{name}/members/`).

## Design

### 1. Route + controller method

Add a GET route in `Main.java` inside the existing `/{groupName}/members` prefix block,
before the existing `post("/invite", ...)`:

```java
membersRoute.get("/", members::list)
            .get("/invite", members::inviteForm)
            .post("/invite", members::invite)
            .post("/{userId}/accept", members::accept)
            ...
```

Add `MembershipController.inviteForm(HTTPRequest req, HTTPResponse res)`. It mirrors
`GroupController.newForm()`:

- Resolve `groupName` from the `GROUP_NAME` request attribute.
- Look up the group via `groupService.findGroup(groupName)`; if absent, return without
  rendering — `GroupSecurity` (installed at the `/app/groups` prefix) has already
  redirected the request to `/app/` with 303 before the controller runs, so the
  group is guaranteed present here in practice. Treat the empty case as defensive only.
- Build the `MainView` via `viewService.buildMainView(user)`.
- Render `pages/groups/invite.jte` with `view`, `group`, an empty
  `InviteRequest` (`new InviteRequest(groupName, "", Role.CONTRIBUTOR)`), and
  `errors=new Errors()`.

Method placement follows the existing controller conventions: instance methods are
ordered alphabetically within their visibility, so `inviteForm` is placed immediately
after `invite` (`"invite"` < `"inviteForm"`) and the private helper `renderInviteForm`
is alphabetized among the other private methods.

### 2. New generic component `web/components/radio-card.jte`

A general-purpose, single-choice card backed by a radio input. It has no knowledge of
roles or any specific domain.

Params:

- `String name` — radio group name.
- `String value` — value submitted when this card is selected.
- `String label` — card title.
- `String description = ""` — card body text.
- `String icon = ""` — optional icon name for the chip; the chip is omitted when empty.
- `boolean checked = false` — whether this option is pre-selected.

Renders a `<label>` wrapping a visually-hidden
`<input type="radio" name="${name}" value="${value}">` followed by the card body. The
card body reuses the visual language of the "Group kinds" cards in `new.jte`: an
icon chip (when `icon` is non-empty), a bold title, and a muted description. Tailwind
`peer-checked:` utilities drive the selected state — a highlighted ring/border on the
card and a filled radio dot indicator. The whole card is clickable because the visible
content is inside the `<label>`.

### 3. New template `web/pages/groups/invite.jte`

Structure mirrors `new.jte`:

- `@param MainView view`, `@param Group group`, `@param InviteRequest inviteRequest`,
  `@param Errors errors = new Errors()`. The submitted email and role are read off
  `inviteRequest` (`inviteRequest.email()`, `inviteRequest.role()`) rather than passed
  as separate scalars. The controller always supplies it, so it has no default.
  `group` is still a separate param — it is the domain `Group` used for the breadcrumb
  name and form action; `inviteRequest.groupName()` is not used for rendering.
  **Naming note:** the param is `inviteRequest`, not `request`. `org.lattejava.web`'s
  `JTETemplates` reserves the model keys `request`/`response` (it binds the live
  `HTTPRequest`/`HTTPResponse` to them), so a `@param InviteRequest request` collides
  and throws `ClassCastException` at render. The controller-side `Map.of` key and the
  template param are therefore `inviteRequest`; the Java local in the controller may
  remain `request`.
- Wrapped in `@template.layout.main(view=view, pageTitle="Invite a member",
  activeNav="groups", activeGroupId=group.name(), content=@`...`)`.
- Breadcrumb: `Groups › {group.name()} › Members › Invite`, using the
  `chevron-right` icon separators as in `new.jte`/`verify.jte`.
- `@template.components.page-header(title="Invite a member", subtitle=...)` — subtitle
  explains the invitee gets an email and stays *invited* until they accept.
- General-error block: same red box pattern as `new.jte` (`errors.generalErrors`).
- `<form class="max-w-2xl" method="post" action="/app/groups/${group.name()}/members/invite">`:
  - Email: `@template.components.input(name="email", value=inviteRequest.email(),
    type="email", label="Email", placeholder="teammate@company.com", required=true,
    autofocus=true, error=emailError)` where `emailError` is derived from
    `errors.fieldErrors.get("email")` exactly as `new.jte` derives `nameError`.
    Note: `InviteRequest`'s compact constructor lowercases `email`, so a redisplayed
    value after a validation error is shown lowercased — acceptable for an email field.
  - Role section heading + a responsive grid containing two `radio-card` calls:

    ```jte
    @template.components.radio-card(name="role", value="CONTRIBUTOR", icon="users",
        label=Role.CONTRIBUTOR.label(), description=Role.CONTRIBUTOR.description(),
        checked=inviteRequest.role() == Role.CONTRIBUTOR)
    @template.components.radio-card(name="role", value="OWNER", icon="shield",
        label=Role.OWNER.label(), description=Role.OWNER.description(),
        checked=inviteRequest.role() == Role.OWNER)
    ```

  - Button row: `Cancel` (ghost, `href="/app/groups/${group.name()}/members/"`) and
    `Send invitation` (primary, `type="submit"`).
- `Role` and `InviteRequest` must be imported in the template
  (`@import org.lattejava.app.model.Role`, `@import org.lattejava.app.model.InviteRequest`).

### 4. Update `web/pages/groups/members.jte`

- Change the "Invite member" button `href` from `?invite=1#invite` to
  `/app/groups/${group.name()}/members/invite`.
- Delete the inline conditional invite `<form>` block (the
  `@if(!errors.empty() || !inviteEmail.isEmpty())` ... `@endif` region) and any
  now-unused `emailError`/`inviteEmail`/`inviteRole` references in this template.
- Remove the `inviteEmail` and `inviteRole` `@param` declarations from this template.

### 5. Update POST `invite()` error handling

In `MembershipController.invite()`, on `ValidationException`, stop calling
`renderMembers(...)`. Instead re-render the full invite page with the submitted values
and errors — the same self-resubmit pattern `GroupController.create()` uses with
`new.jte`:

- Resolve the group as in `inviteForm()` (defensive empty-check only; `GroupSecurity`
  has already 303-redirected missing-group requests away).
- Render `pages/groups/invite.jte` with `view`, `group`, the same `InviteRequest`
  already constructed for the `membershipService.invite(...)` call, and `e.errors()`.
  No new request object is built for the error branch — the one passed to the service
  is reused.

The success path (redirect to `/app/groups/{name}/members/`, 303) is unchanged.

To avoid duplicating the group-lookup + render between `inviteForm()` and the
`invite()` error branch, extract a private helper
`renderInviteForm(req, res, groupName, request, errors)` (where `request` is an
`InviteRequest`) and call it from both. It performs the group lookup + 404 guard,
builds the `MainView`, and renders `invite.jte` with the model keys `view`, `group`,
`inviteRequest` (the `InviteRequest`; see the naming note in §3 — the key is
`inviteRequest`, not `request`), and `errors`. The helper short-circuits if the
group lookup is empty (defensive — `GroupSecurity` will normally have already
redirected such requests to `/app/`).

### 6. Cleanup of the members detail-tab chain

With the inline form gone, the invite-specific parameters are no longer needed on the
members tab:

- `MembershipController.renderMembers()` — drop the `inviteEmail` and `inviteRole`
  parameters (only `list()` calls it now). Remove the corresponding entries from the
  `Map.of(...)` passed to `detail.jte`.
- `web/pages/groups/detail.jte` — remove `inviteEmail`/`inviteRole` from the
  `@param` list and from the `@template.pages.groups.members(...)` call.
- `web/pages/groups/members.jte` — already covered in section 4.

`errors` continues to thread through `detail.jte` (the settings tab still uses it); only
the invite-specific params are removed.

## Affected files

| File | Change |
|------|--------|
| `src/main/java/org/lattejava/app/Main.java` | Add `get("/invite", members::inviteForm)` route |
| `src/main/java/org/lattejava/app/controller/MembershipController.java` | Add `inviteForm()`, add `renderInviteForm()` helper, rework `invite()` error branch, simplify `renderMembers()` signature |
| `web/components/radio-card.jte` | New generic radio-card component |
| `web/pages/groups/invite.jte` | New full-page invite form |
| `web/pages/groups/members.jte` | Repoint button, delete inline form + invite params |
| `web/pages/groups/detail.jte` | Drop `inviteEmail`/`inviteRole` param plumbing |

## Testing

- Existing tests boot a real `Main` and drive the FusionAuth flow (`OIDCTestFixture`),
  asserting on rendered HTML and redirect chains. Add/extend a test under
  `src/test/java/` to:
  - GET `/app/groups/{group}/members/invite` returns 200 and the rendered page
    contains the email field and both role cards (Contributor/Owner labels).
  - POST `/app/groups/{group}/members/invite` with a valid new email redirects 303 to
    `/app/groups/{group}/members/` and the member appears as PENDING.
  - POST with an invalid/duplicate email re-renders the invite page (200) showing the
    field error rather than redirecting.
  - GET the invite page for a nonexistent group redirects 303 to `/app/`
    (`GroupSecurity` short-circuits before the controller runs).
- Run the full suite with `latte test` (requires FusionAuth on `:9013` and D1 network
  access, per project setup).

## Conventions

- Java: copyright header, module imports, acronym casing, alphabetization, error
  values in `[brackets]` — per `.claude/rules/`.
- Web: route registration order, trailing-slash rules (the invite form is a form page,
  so **no** trailing slash: `/app/groups/{name}/members/invite`), `Map.of()` over
  `HashMap`, compact JTE component calls — per `.claude/rules/web-conventions.md`.
