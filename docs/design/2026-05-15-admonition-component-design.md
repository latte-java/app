# Admonition Component — Design

## Problem

Inline admonition banners (a colored, rounded box with a leading icon and a
short message) are hand-written as duplicated Tailwind class strings across
templates. The same ~12-class string is repeated 10 times across two files,
with tone color and icon varied by hand. Changing the look means editing every
copy; a typo in one copy drifts silently.

## Goal

A single `web/components/admonition.jte` component. Refactor all 10 existing
call sites to use it, preserving rendered output exactly (same colors, icons,
text, and spacing).

## Component

**File:** `web/components/admonition.jte`

**Parameters:**

```
@param gg.jte.Content content
@param String tone = "neutral"
@param String icon = null     // override; null means derive from tone
@param String cssClass = ""   // passthrough, e.g. "mt-4"
```

`content` is a JTE content block (same pattern as `card.jte` /
`empty-state.jte`) so interpolated values render inside the message.

**Tone → colors + default icon** (lifted verbatim from the current markup;
tone vocabulary aligned with `badge.jte`):

| tone      | colors                                                                | default icon |
|-----------|-----------------------------------------------------------------------|--------------|
| `success` | `bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300` | `check`      |
| `warn`    | `bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300`         | `alert`      |
| `danger`  | `bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300`                 | `alert`      |
| `neutral` | `bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300`           | `clock`      |

The `text-{tone}-700` color also colors the icon via `currentColor` (the
`icon` component renders `fill="currentColor"`), so no separate icon color is
needed.

**Markup:**

```
<div class="flex items-center gap-2.5 px-3.5 py-3 rounded-md text-sm font-medium ${toneCls} ${cssClass}">
  @template.components.icon(name = resolvedIcon, size = 14)
  ${content}
</div>
```

`resolvedIcon` is `icon` when non-null, otherwise the tone default from the
table above. `toneCls` is resolved with the same `!{ ... }` ternary style used
in `badge.jte`.

## Spacing (`mt-4`)

The existing usages are inconsistent: the 6 GitHub-status banners and the
`detail.jte` banner carry `mt-4`; the 3 banners nested inside verify step 3 do
not. Callers pass `cssClass = "mt-4"` only where the original had it, so the
refactor preserves spacing exactly. No `mt-4` is baked into the component.

## Refactor scope (10 call sites)

All colors, icons, and message text are preserved unchanged.

**`web/pages/groups/verify.jte`** — 9 sites:

- GitHub status block: `verified` (success, `mt-4`), `not_linked` (warn,
  `mt-4`), `unauthorized` (warn, `mt-4`), `not_authorized` (danger, `mt-4`),
  `oauth_failed` (danger, `mt-4`), `link_failed` (danger, `mt-4`)
- Verify step 3 block: DNS found + matches (success, no margin), found but no
  match (danger, no margin), waiting on DNS (**neutral**, no margin)

**`web/pages/groups/detail.jte`** — 1 site:

- `oauth_failed` banner (danger, `mt-4`)

## Out of scope

Not refactored — different visual pattern, would need a different component:

- `w-9 h-9 rounded-lg` icon-chip blocks (`overview.jte`, `new.jte`,
  `members.jte`)
- `border … rounded-lg` form-error boxes (`new.jte`, `settings.jte`)
- numbered step circles (`verify.jte`)

## Testing

The existing TestNG suite boots a real server and renders the `verify` and
`detail` pages. A green `latte test` confirms the templates still compile and
render with the new component. Visual parity is guaranteed by reusing the exact
class strings; the only change is where they live.
