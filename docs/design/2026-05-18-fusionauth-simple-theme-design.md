# FusionAuth Simple Theme — Design

**Date:** 2026-05-18
**Status:** Approved (design)
**Branch:** `features/fusionauth-theme`

## Problem

FusionAuth's hosted identity pages (login, set/change-password, OAuth authorize/consent,
error pages) currently render with FusionAuth's stock default theme. The Latte Java app is
Tailwind v4 styled with its own logo and palette. A user moving from the app into a
FusionAuth-hosted page sees a jarring, unbranded screen.

## Goal

Provision a single **tenant-level FusionAuth Simple Theme** so every hosted identity page
carries Latte Java branding (logo, colors, fonts), provisioned entirely through
`kickstart.json` so it is reproducible across developers.

Simple Themes are a FusionAuth feature (available since 1.51.0; the project runs
`fusionauth/fusionauth-app:latest`, so the minimum is satisfied). A Simple Theme is a
`variables` map of colors/typography/layout — no FreeMarker, no HTML.

## Scope

In scope:

- One `type: "simple"` theme created via the Themes API in the kickstart.
- Light appearance (a Simple Theme is a single fixed appearance; no auto light/dark
  switching). This matches the app's default chrome.
- Tenant-level assignment (free; no license required). Single-tenant/single-app setup, so
  this covers every hosted page.
- Theme variables mapped from the app's Tailwind tokens.
- A test asserting the theme is applied to the hosted login page.

Out of scope:

- Dark-mode variant.
- Email-template restyling (the invite / set-password templates stay as they are).
- Application-specific themes (a paid FusionAuth feature; license is only optionally
  configured).
- Custom FreeMarker / advanced theme / custom stylesheet.

## Design

### Theme variables

Mapped from the app's Tailwind palette (slate neutrals, sky primary accent, red
destructive) and font tokens (`--font-sans` Inter, `--font-mono` JetBrains Mono):

| Variable                      | Value                                                                                              | Source             |
|-------------------------------|----------------------------------------------------------------------------------------------------|--------------------|
| `pageBackgroundColor`         | `#f8fafc`                                                                                          | slate-50           |
| `panelBackgroundColor`        | `#ffffff`                                                                                          | white              |
| `fontColor`                   | `#0f172a`                                                                                          | slate-900          |
| `primaryButtonColor`          | `#0284c7`                                                                                          | sky-600            |
| `primaryButtonFocusColor`     | `#0369a1`                                                                                          | sky-700            |
| `primaryButtonTextColor`      | `#ffffff`                                                                                          | white              |
| `primaryButtonTextFocusColor` | `#ffffff`                                                                                          | white              |
| `linkTextColor`               | `#0284c7`                                                                                          | sky-600            |
| `linkTextFocusColor`          | `#0369a1`                                                                                          | sky-700            |
| `deleteButtonColor`           | `#dc2626`                                                                                          | red-600            |
| `deleteButtonFocusColor`      | `#b91c1c`                                                                                          | red-700            |
| `deleteButtonTextColor`       | `#ffffff`                                                                                          | white              |
| `deleteButtonTextFocusColor`  | `#ffffff`                                                                                          | white              |
| `inputBackgroundColor`        | `#ffffff`                                                                                          | white              |
| `inputTextColor`              | `#0f172a`                                                                                          | slate-900          |
| `borderRadius`                | `8px`                                                                                              | app's `rounded-lg` |
| `fontFamily`                  | `Inter, ui-sans-serif, system-ui, sans-serif`                                                      | `--font-sans`      |
| `monoFontFamily`              | `JetBrains Mono, ui-monospace, monospace`                                                          | `--font-mono`      |
| `logoImageURL`                | `http://localhost:8080/static/images/logo.svg`                                                     | app static asset   |
| `favicons`                    | `http://localhost:8080/static/favicon-32x32.png`, `http://localhost:8080/static/favicon-16x16.png` | app static assets  |

The FusionAuth Simple Theme API requires the **complete** `variables` set, not a
partial override — a `POST /api/theme` with only the branded subset is rejected `400`
with `[blank]` field errors for every unspecified required variable. The remaining
required variables are therefore filled with palette-consistent defaults:

| Variable               | Value     | Source                 |
|------------------------|-----------|------------------------|
| `alertBackgroundColor` | `#e0f2fe` | sky-100                |
| `alertFontColor`       | `#0c4a6e` | sky-900                |
| `errorFontColor`       | `#b91c1c` | red-700                |
| `errorIconColor`       | `#dc2626` | red-600                |
| `infoIconColor`        | `#0284c7` | sky-600                |
| `iconColor`            | `#475569` | slate-600              |
| `iconBackgroundColor`  | `#f1f5f9` | slate-100              |
| `inputIconColor`       | `#94a3b8` | slate-400              |
| `monoFontColor`        | `#0f172a` | slate-900 (= fontColor) |

`backgroundImageURL`, `backgroundSize`, and `footerDisplay` are *not* required and are
left unset (FA defaults; no background image, panel on a slate-50 page).

Notes:

- The exact `logoImageSize` value is a tuning detail left to implementation.
- These values are derived from Tailwind's default palette for the named tokens; if any
  app token is later customized, the spec table is the source of truth to re-sync.

### Kickstart wiring

Follow the existing email-template pattern in `src/main/fusionauth/kickstart/kickstart.json`
(fixed UUID variables + `apiRequest`-style request blocks):

1. Add a `themeId` UUID to the kickstart `variables` section, alongside the existing
   `inviteEmailTemplateId` / `setPasswordEmailTemplateId`.
2. Add a request block that creates the theme at `/api/theme/#{themeId}` (HTTP method
   per the existing kickstart request-block convention) with the body
   `{ "theme": { "name": "Latte Java", "type": "simple", "variables": { … } } }`.
3. Add a request block that updates the tenant (`/api/tenant/#{tenantId}`) to set
   `tenant.themeId = #{themeId}`, so the theme applies to all hosted pages for the tenant.

The Themes API requires a Global API Key; the kickstart already runs with full admin
privileges, so no extra credential is needed.

### Image-URL constraint and the cross-origin block

FusionAuth Simple Themes cannot host images — `logoImageURL` and `favicons` must be
fully-qualified URLs (FA validates the value as a URL and rejects a `data:` URI with
`400 [invalidURL]`, so inlining the image is not an option). The natural source is the
app's own static server, hardcoded to `http://localhost:8080/static/...` — consistent
with the other hardcoded localhost URLs already in the kickstart's email templates, and
overridden in a production deployment (accepted pre-existing pattern, not new debt).

FusionAuth renders the logo on its hosted login page (origin `http://localhost:9013`) as
`<img id="imgThemeLogo">` driven by a CSS custom property
(`--img-logo: url('http://localhost:8080/static/images/logo.svg')`). The image is thus a
**cross-origin subresource** of the FA page. The Latte app applies a strict global
`SecurityHeaders` policy that sets `Cross-Origin-Resource-Policy: same-origin` on every
response, including `/static/*`. A browser honors that by refusing to deliver the asset to
the cross-origin FA page, so the logo (and favicons) silently fail to render even though
the URL is correctly present in the page.

**Resolution:** relax `Cross-Origin-Resource-Policy` to `cross-origin` for `/static/*`
responses only — public branding assets are intended to be embeddable — leaving every
other response `same-origin`. This is done in `Main` with a `/static`-scoped middleware
installed before the static file handler that overrides the value the global
`SecurityHeaders` set (`SecurityHeaders.handle` only sets a header when unset and the
global middleware is collected root-first, so the override must be unconditional and
downstream). Requires the `web` module's `SecurityHeaders.defaults()` / `empty()` API
(the older `builder()` form was removed); `Main` is migrated accordingly.

## Testing

`FusionAuthThemeTest` has two assertions:

1. `hostedLoginPageUsesLatteTheme` — GET the FA hosted login page and assert the HTML
   carries the Latte logo URL (`/static/images/logo.svg`), proving the custom tenant theme
   is applied rather than FA's stock default.
2. `staticAssetsAllowCrossOriginEmbedding` — assert `/static/*` responses carry
   `Cross-Origin-Resource-Policy: cross-origin` (so the cross-origin FA page can actually
   load the logo/favicons) while a non-static response still carries `same-origin` (the
   relaxation is scoped, not a global weakening). This is the regression guard for the
   cross-origin block; a string-only "URL present in HTML" check passes even when the
   browser would refuse the image, so the header contract is asserted explicitly.

## Risks / open points

- **Logo asset choice:** `logo.svg` (full wordmark) is the default; `logo-cup.svg` (icon)
  is an alternative. Implementation may adjust `logoImageURL` + `logoImageSize` after a
  visual check. Low risk — one-line variable change.
- **Theme assertion brittleness:** asserting on a logo URL is more stable than asserting
  on a hex color (FA may normalize CSS). The test should prefer the logo-URL marker.
- **Re-applying kickstart:** the theme only provisions on a fresh kickstart
  (`docker compose down -v && up -d`), which is destructive to FA state. Confirm with the
  developer before suggesting that reset (existing project convention).
