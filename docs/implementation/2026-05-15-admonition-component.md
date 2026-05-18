# Admonition Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the duplicated inline admonition banner markup into a single reusable JTE component and refactor all 10 call sites to use it without changing rendered output.

**Architecture:** A new `web/components/admonition.jte` takes a `gg.jte.Content` body, a `tone` (success/warn/danger/neutral) that resolves both the color classes and a default icon, an optional `icon` override, and a `cssClass` passthrough for per-call-site margins. Tone resolution uses the same `!{ ... }` ternary idiom as `badge.jte`. Verification is by JTE compilation + the existing TestNG suite, which boots a real server and renders the `verify`/`detail` pages.

**Tech Stack:** JTE 3.x server-side templates, Tailwind v4 utility classes, `latte` build tool, TestNG.

---

### Verification note (read before starting)

There is no behavior change in this work — the rendered HTML must be byte-identical. So the "test" is not a new unit test; it is:

1. **JTE compilation** — JTE compiles every template when the server boots. A malformed template fails the suite at `@BeforeSuite`.
2. **The existing TestNG suite** — boots `Main` and exercises the running server, which renders `verify` and `detail`. Command: `latte test`. Expected: BUILD SUCCESSFUL / all tests pass.
3. **Visual parity by construction** — every class string is moved verbatim into the component; call sites only choose `tone`/`icon`/`cssClass`.

Each task ends by running `latte test` and committing only after it is green. Do not introduce a fabricated test for unchanged output.

Prerequisite: FusionAuth on `:9011` and D1 network access (see `CLAUDE.md`) — the existing suite requires these regardless of this change.

---

### Task 1: Create the `admonition` component

**Files:**
- Create: `web/components/admonition.jte`

- [ ] **Step 1: Write the component**

Create `web/components/admonition.jte` with exactly this content:

```
<%--
    Admonition — a colored, rounded banner with a leading icon and a short
    message. Tone drives the color set and the default icon; pass `icon` to
    override, and `cssClass` for per-site spacing (e.g. "mt-4").
--%>
@param gg.jte.Content content
@param String tone = "neutral"
@param String icon = null
@param String cssClass = ""

!{String toneCls = tone.equals("success") ? "bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300"
               : tone.equals("warn")    ? "bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300"
               : tone.equals("danger")  ? "bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300"
               :                          "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300";}
!{String resolvedIcon = icon != null ? icon
               : tone.equals("success") ? "check"
               : tone.equals("neutral") ? "clock"
               :                          "alert";}

<div class="flex items-center gap-2.5 px-3.5 py-3 rounded-md text-sm font-medium ${toneCls} ${cssClass}">
  @template.components.icon(name = resolvedIcon, size = 14)
  ${content}
</div>
```

Rationale for `resolvedIcon`: `success`→`check`, `neutral`→`clock`, and both `warn` and `danger`→`alert` (matches the current markup exactly).

- [ ] **Step 2: Verify the component compiles**

Run: `latte test`
Expected: BUILD SUCCESSFUL, all tests pass. (JTE compiles `admonition.jte` even though nothing references it yet; a syntax error here fails the build.)

- [ ] **Step 3: Commit**

```bash
git add web/components/admonition.jte
git commit -m "feat(ui): add admonition component"
```

---

### Task 2: Refactor `detail.jte` (1 call site)

**Files:**
- Modify: `web/pages/groups/detail.jte` (the `oauth_failed` block, around lines 26-31)

- [ ] **Step 1: Replace the banner**

Find this block:

```
  @if("oauth_failed".equals(status))
    <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
      @template.components.icon(name = "alert", size = 14)
      GitHub didn't authorize the connection. Try "Connect GitHub" again.
    </div>
  @endif
```

Replace it with:

```
  @if("oauth_failed".equals(status))
    @template.components.admonition(tone = "danger", cssClass = "mt-4") {
      GitHub didn't authorize the connection. Try "Connect GitHub" again.
    }
  @endif
```

- [ ] **Step 2: Run the suite**

Run: `latte test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add web/pages/groups/detail.jte
git commit -m "refactor(ui): use admonition component in detail.jte"
```

---

### Task 3: Refactor the 6 GitHub-status banners in `verify.jte`

**Files:**
- Modify: `web/pages/groups/verify.jte` (the `@if("verified")...@endif` status chain, around lines 38-67)

- [ ] **Step 1: Replace each banner**

Find this chain:

```
      @if("verified".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 text-sm font-medium">
          @template.components.icon(name = "check", size = 14)
          Verified.
        </div>
      @elseif("not_linked".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 text-sm font-medium">
          @template.components.icon(name = "alert", size = 14)
          Click "Connect GitHub" to link your GitHub account, then try again.
        </div>
      @elseif("unauthorized".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 text-sm font-medium">
          @template.components.icon(name = "alert", size = 14)
          Your GitHub link expired. Click "Connect GitHub" to re-link.
        </div>
      @elseif("not_authorized".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
          @template.components.icon(name = "alert", size = 14)
          Your GitHub account isn't a member of the org or doesn't match the personal account in this group name.
        </div>
      @elseif("oauth_failed".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
          @template.components.icon(name = "alert", size = 14)
          GitHub didn't authorize the connection. Try "Connect GitHub" again.
        </div>
      @elseif("link_failed".equals(status))
        <div class="mt-4 flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
          @template.components.icon(name = "alert", size = 14)
          The link with GitHub failed. You can try connecting again, but if the problem persists, open a GitHub issue with the Latte Project.
        </div>
      @endif
```

Replace it with:

```
      @if("verified".equals(status))
        @template.components.admonition(content = @`Verified.`, tone = "success", cssClass = "mt-4")
      @elseif("not_linked".equals(status))
        @template.components.admonition(content = @`Click "Connect GitHub" to link your GitHub account, then try again.`, tone = "warn", cssClass = "mt-4")
      @elseif("unauthorized".equals(status))
        @template.components.admonition(content = @`Your GitHub link expired. Click "Connect GitHub" to re-link.`, tone = "warn", cssClass = "mt-4")
      @elseif("not_authorized".equals(status))
        @template.components.admonition(content = @`Your GitHub account isn't a member of the org or doesn't match the personal account in this group name.`, tone = "danger", cssClass = "mt-4")
      @elseif("oauth_failed".equals(status))
        @template.components.admonition(content = @`GitHub didn't authorize the connection. Try "Connect GitHub" again.`, tone = "danger", cssClass = "mt-4")
      @elseif("link_failed".equals(status))
        @template.components.admonition(content = @`The link with GitHub failed. You can try connecting again, but if the problem persists, open a GitHub issue with the Latte Project.`, tone = "danger", cssClass = "mt-4")
      @endif
```

- [ ] **Step 2: Run the suite**

Run: `latte test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add web/pages/groups/verify.jte
git commit -m "refactor(ui): use admonition for verify github-status banners"
```

---

### Task 4: Refactor the 3 verify-step-3 banners in `verify.jte`

**Files:**
- Modify: `web/pages/groups/verify.jte` (the step-3 DNS-check block, around lines 124-140)

- [ ] **Step 1: Replace each banner**

Find this block:

```
            @if(verification.dnsRecordFound() && verification.valueMatches())
              <div
                  class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 text-sm font-medium">
                @template.components.icon(name = "check", size = 14)
                DNS record found and value matches. You're verified.
              </div>
            @elseif(verification.dnsRecordFound())
              <div
                  class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
                @template.components.icon(name = "alert", size = 14)
                Record found but the value doesn't match.
              </div>
            @else
              <div
                  class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 text-sm font-medium">
                @template.components.icon(name = "clock", size = 14)
                Waiting on DNS. Last checked ${verification.lastCheckedAt().toString()}.
```

Note: the `@else` branch continues past line 140 — preserve everything after
`Last checked ${verification.lastCheckedAt().toString()}.` up to and including
its closing `</div>` (and the surrounding `@endif`) unchanged in structure;
only the wrapper changes. Replace the three banner wrappers so the block reads:

```
            @if(verification.dnsRecordFound() && verification.valueMatches())
              @template.components.admonition(content = @`DNS record found and value matches. You're verified.`, tone = "success")
            @elseif(verification.dnsRecordFound())
              @template.components.admonition(content = @`Record found but the value doesn't match.`, tone = "danger")
            @else
              @template.components.admonition(content = @`Waiting on DNS. Last checked ${verification.lastCheckedAt().toString()}.`, tone = "neutral")
            @endif
```

The `neutral` tone resolves to `bg-slate-100 dark:bg-slate-800
text-slate-600 dark:text-slate-300` and the `clock` icon — identical to the
original `@else` branch. No `cssClass` is passed (these had no `mt-4`).

If the original `@else` body contained markup beyond the single sentence shown
in the design (e.g. a follow-up element after "Last checked …"), keep that
markup inside the `content = @`...`` block verbatim (the `@`...`` literal may
span multiple lines and contain `${...}` interpolations and nested
`@template...` calls).

> **JTE syntax note (plan correction):** JTE 3.2.1 has NO trailing-brace
> `@template.components.foo(...) { body }` form. Pass the body as the named
> content parameter: `@template.components.admonition(content = @`...`, tone =
> "...", ...)`. This matches the established idiom at `web/pages/dashboard.jte`
> and `web/pages/groups/new.jte`. Task 2 was implemented this way.

- [ ] **Step 2: Run the suite**

Run: `latte test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add web/pages/groups/verify.jte
git commit -m "refactor(ui): use admonition for verify step-3 dns banners"
```

---

### Task 5: Final verification

- [ ] **Step 1: Confirm no admonition markup remains**

Run:

```bash
grep -rn 'gap-2.5 px-3.5 py-3 rounded-md' web/pages web/components
```

Expected: no output (the only remaining occurrence of these classes is inside
`web/components/admonition.jte`, which the path filter excludes — confirm by
running `grep -rn 'gap-2.5 px-3.5 py-3 rounded-md' web/components/admonition.jte`
and seeing exactly one match).

- [ ] **Step 2: Full suite**

Run: `latte test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Visual spot-check (manual, optional but recommended)**

Run `latte run`, sign in, and view a group's verify page (both the GitHub-link
state and the DNS step-3 states) and a detail page with `?status=oauth_failed`.
Confirm the banners look identical to before (color, icon, spacing).

---

## Self-Review

- **Spec coverage:** Component params (Task 1) ✓. Tone→color+icon table (Task 1 `toneCls`/`resolvedIcon`) ✓. `mt-4` passthrough preserved per-site (Tasks 2-3 pass it, Task 4 omits it) ✓. All 10 call sites: detail.jte ×1 (Task 2), verify github-status ×6 (Task 3), verify step-3 ×3 (Task 4) = 10 ✓. Out-of-scope blocks untouched ✓. Testing via `latte test` + parity by construction ✓.
- **Placeholder scan:** No TBD/TODO; every code step shows complete before/after content.
- **Type consistency:** Param names `content`/`tone`/`icon`/`cssClass` and helper names `toneCls`/`resolvedIcon` are used identically across all tasks; tone literals `success`/`warn`/`danger`/`neutral` match the component's branches exactly.
