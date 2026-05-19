# FusionAuth Simple Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Brand FusionAuth's hosted identity pages to match the Latte Java app via a kickstart-provisioned, tenant-level FusionAuth Simple Theme.

**Architecture:** Add a fixed `themeId` UUID and a `POST /api/theme/#{themeId}` request (a `type: "simple"` theme whose `variables` map encodes the app's Tailwind palette/fonts/logo) to `src/main/fusionauth/kickstart/kickstart.json`, then point the existing tenant `PATCH` block at it via `tenant.themeId`. A TestNG test drives the FusionAuth hosted login page and asserts the Latte logo URL appears, proving the theme is applied.

**Tech Stack:** FusionAuth Themes API (Simple Themes, FA ≥ 1.51.0), JSON kickstart, Java 25 + TestNG, `java.net.http.HttpClient`.

---

## Background the engineer needs

- **What a FusionAuth Simple Theme is:** a theme of `type: "simple"` whose appearance is driven entirely by a `variables` JSON map (colors, fonts, border radius, logo/favicon URLs). No FreeMarker, no HTML. Created via `POST /api/theme/{themeId}`. Available since FusionAuth 1.51.0; this project runs `fusionauth/fusionauth-app:latest`, so the version is fine.
- **The `variables` map must be COMPLETE.** FusionAuth validates the full required variable set on `POST /api/theme`; a partial/branded-subset map is rejected `400` with a `[blank]theme.variables.<name>` field error per missing variable. The JSON in Task 2 Step 2 already includes the full required set (branded values + palette-consistent defaults for `alert*`, `error*`, `*IconColor`, `monoFontColor`). `backgroundImageURL`, `backgroundSize`, and `footerDisplay` are optional and intentionally omitted.
- **Kickstart model:** `src/main/fusionauth/kickstart/kickstart.json` is replayed by FusionAuth on a *fresh* boot. `variables` holds fixed UUIDs/strings; `requests` is an ordered list of API calls. Requests run top-to-bottom, so the theme must be created *before* the tenant `PATCH` that references it. Existing email templates show the exact pattern: a `POST /api/<resource>/#{fixedUuidVariable}` with a JSON body.
- **Image hosting constraint:** Simple Themes cannot host images. `logoImageURL`/`favicons` must be fully-qualified URLs. The app's own static server serves them, and the browser loading the FA login page can reach `http://localhost:8080/static/...`. These URLs are hardcoded to localhost, exactly like the existing kickstart email-template links — accepted for the local-dev kickstart.
- **Re-applying kickstart is destructive.** The kickstart only replays on a fresh FusionAuth volume. Applying this change to a running stack requires `docker compose --profile mailcatcher down -v && docker compose --profile mailcatcher up -d` from `src/main/fusionauth/`, which **wipes all FusionAuth state**. Per project convention this reset must be explicitly confirmed by the developer before running — the plan flags exactly where.
- **How the test reaches the themed page:** `BaseTest` boots a real `Main` and exposes `main.oidcConfig`. A GET (no auth cookies) to `main.oidcConfig.authorizeEndpoint()` with the app's `client_id`/`redirect_uri` returns FusionAuth's hosted login HTML. With the Simple Theme applied, that HTML references the Latte logo URL. `OIDCTestFixture.fetchAuthorizationCode` (in the sibling `org.lattejava.web` module) already does exactly this GET; the new test does a minimal version of it and asserts on the body.

## File structure

- **Modify:** `src/main/fusionauth/kickstart/kickstart.json` — add `themeId` variable, add the theme-creation request, add `themeId` to the existing tenant `PATCH` body.
- **Create:** `src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java` — one TestNG test asserting the hosted login page carries the Latte logo. Lives in `org.lattejava.app.tests` (already `opens` to TestNG via `module-info.java`); extends `BaseTest` so it is auto-discovered with no registration needed.

- **Modify:** `src/test/java/module-info.java` — add `requires java.net.http;` (alphabetized). The test module does not already read `java.net.http`: `org.lattejava.web` requires it non-transitively, so JPMS does not expose it downstream. The test uses `HttpClient`/`HttpRequest`/`HttpResponse` directly and needs this `requires`. (Resolved during Task 1.)

No production Java changes. No new external dependencies, no `project.latte` change. The Task 1 test file also needs an explicit `import org.lattejava.app.Main;` to disambiguate from `org.testng.reporters.jq.Main` (both are visible via module imports) — already reflected in the Task 1 code below.

---

### Task 1: Failing test — hosted login page must carry the Latte logo

**Files:**
- Create: `src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java` with exactly this content:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module java.net.http;
import module org.lattejava.app;
import module org.testng;

/**
 * Verifies the kickstart-provisioned tenant-level FusionAuth Simple Theme is applied to the hosted identity pages.
 * The hosted login HTML must reference the Latte Java logo served by the app's static server, which proves the
 * custom theme is in effect rather than FusionAuth's stock default theme.
 *
 * @author Brian Pontarelli
 */
@Test
public class FusionAuthThemeTest extends BaseTest {
  @Test
  public void hostedLoginPageUsesLatteTheme() throws Exception {
    String redirectURI = "http://localhost:" + Main.PORT + main.oidcConfig.callbackPath();
    String query = "client_id=" + URLEncoder.encode(main.oidcConfig.clientId(), StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectURI, StandardCharsets.UTF_8)
        + "&response_type=code"
        + "&scope=" + URLEncoder.encode(String.join(" ", main.oidcConfig.scopes()), StandardCharsets.UTF_8)
        + "&state=themetest";
    URI authorize = URI.create(main.oidcConfig.authorizeEndpoint() + "?" + query);

    try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder(authorize).GET().build(),
          HttpResponse.BodyHandlers.ofString());

      Assert.assertEquals(response.statusCode(), 200,
          "Expected the hosted login page, got [" + response.statusCode() + "]: [" + response.body() + "]");
      Assert.assertTrue(response.body().contains("/static/images/logo.svg"),
          "Hosted login page is not using the Latte Simple Theme (no Latte logo URL in the HTML). Body: ["
              + response.body() + "]");
    }
  }
}
```

- [ ] **Step 2: Run the test and verify it fails**

This requires FusionAuth running on `:9011` and the app's prerequisites (D1/R2 config, per `CLAUDE.md`). Run a single test:

```bash
latte test --test=org.lattejava.app.tests.FusionAuthThemeTest
```

Expected: **FAIL** on the `assertTrue` — the body does not contain `/static/images/logo.svg` because FusionAuth is still serving its stock default theme (no theme provisioned yet). If it fails on `assertEquals(...200...)` instead, stop and investigate the authorize URL/redirect registration before continuing — the assertion target is the logo, not the status.

- [ ] **Step 3: Commit the failing test**

```bash
git add src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java
git commit -m "test: assert FusionAuth hosted login uses the Latte Simple Theme (red)"
```

---

### Task 2: Provision the Simple Theme in the kickstart

**Files:**
- Modify: `src/main/fusionauth/kickstart/kickstart.json`

- [ ] **Step 1: Add the `themeId` variable**

In `kickstart.json`, in the `variables` object, add a fixed `themeId` UUID immediately after the `setPasswordEmailTemplateId` line. Change:

```json
    "inviteEmailTemplateId": "a4db4962-efd8-476d-af15-932567f337b8",
    "setPasswordEmailTemplateId": "9ada7df9-914e-4bd6-9e24-6c11abb1ed3f",
    "emailFieldId":    "#{UUID()}",
```

to:

```json
    "inviteEmailTemplateId": "a4db4962-efd8-476d-af15-932567f337b8",
    "setPasswordEmailTemplateId": "9ada7df9-914e-4bd6-9e24-6c11abb1ed3f",
    "themeId": "c1d7e3a4-6b2f-4e8a-9f1c-5a0d8b3e2f10",
    "emailFieldId":    "#{UUID()}",
```

(The `themeId` UUID is a fixed, arbitrary, valid v4 UUID — reused so re-applying the kickstart is idempotent, exactly like the email-template IDs.)

- [ ] **Step 2: Add the theme-creation request before the tenant PATCH**

In the `requests` array, the `POST /api/email/template/#{setPasswordEmailTemplateId}` block is immediately followed by the `PATCH /api/tenant/#{defaultTenantId}` block. Insert a new request block between them — after the closing `},` of the set-password email-template request and before the `{ "method": "PATCH", "url": "/api/tenant/#{defaultTenantId}", ...` block. Insert exactly:

```json
    {
      "method": "POST",
      "url": "/api/theme/#{themeId}",
      "body": {
        "theme": {
          "name": "Latte Java",
          "type": "simple",
          "variables": {
            "pageBackgroundColor": "#f8fafc",
            "panelBackgroundColor": "#ffffff",
            "fontColor": "#0f172a",
            "primaryButtonColor": "#0284c7",
            "primaryButtonFocusColor": "#0369a1",
            "primaryButtonTextColor": "#ffffff",
            "primaryButtonTextFocusColor": "#ffffff",
            "linkTextColor": "#0284c7",
            "linkTextFocusColor": "#0369a1",
            "deleteButtonColor": "#dc2626",
            "deleteButtonFocusColor": "#b91c1c",
            "deleteButtonTextColor": "#ffffff",
            "deleteButtonTextFocusColor": "#ffffff",
            "inputBackgroundColor": "#ffffff",
            "inputTextColor": "#0f172a",
            "inputIconColor": "#94a3b8",
            "iconColor": "#475569",
            "iconBackgroundColor": "#f1f5f9",
            "alertBackgroundColor": "#e0f2fe",
            "alertFontColor": "#0c4a6e",
            "errorFontColor": "#b91c1c",
            "errorIconColor": "#dc2626",
            "infoIconColor": "#0284c7",
            "monoFontColor": "#0f172a",
            "borderRadius": "8px",
            "fontFamily": "Inter, ui-sans-serif, system-ui, sans-serif",
            "monoFontFamily": "JetBrains Mono, ui-monospace, monospace",
            "logoImageURL": "http://localhost:8080/static/images/logo.svg",
            "logoImageSize": "200px",
            "favicons": [
              { "href": "http://localhost:8080/static/favicon-32x32.png", "rel": "icon", "type": "image/png" },
              { "href": "http://localhost:8080/static/favicon-16x16.png", "rel": "icon", "type": "image/png" }
            ]
          }
        }
      }
    },
```

After the edit, the request order around the tenant patch must read: set-password email template → **theme** → tenant patch. The theme is created before the tenant references it.

- [ ] **Step 3: Wire the theme into the existing tenant PATCH**

Find the existing tenant patch block (it sets `issuer` and `emailConfiguration`). Add `themeId` to the `tenant` object. Change:

```json
      "body": {
        "tenant": {
          "issuer": "http://localhost:9011",
          "emailConfiguration": {
```

to:

```json
      "body": {
        "tenant": {
          "issuer": "http://localhost:9011",
          "themeId": "#{themeId}",
          "emailConfiguration": {
```

(Extend the existing tenant patch rather than adding a second tenant request — there is already exactly one `PATCH /api/tenant/#{defaultTenantId}` and FusionAuth applies the whole `tenant` object.)

- [ ] **Step 4: Validate the JSON is well-formed**

```bash
python3 -m json.tool src/main/fusionauth/kickstart/kickstart.json > /dev/null && echo "JSON OK"
```

Expected: `JSON OK` (no parse error). If it errors, fix the comma/brace placement from Steps 2–3 before continuing.

- [ ] **Step 5: Commit the kickstart change**

```bash
git add src/main/fusionauth/kickstart/kickstart.json
git commit -m "feat: provision a tenant-level FusionAuth Simple Theme via kickstart"
```

---

### Task 3: Re-apply the kickstart and verify green

**Files:** none (operational + verification only)

- [ ] **Step 1: Get explicit confirmation for the destructive reset**

The new theme only takes effect on a fresh kickstart replay, which **destroys all local FusionAuth state** (users, tenant config, IDP). Per project convention, do **not** run this unprompted. Ask the developer to confirm, then have them (or, once confirmed, run) the reset from the `src/main/fusionauth/` directory:

```bash
cd src/main/fusionauth && docker compose --profile mailcatcher down -v && docker compose --profile mailcatcher up -d
```

Wait for FusionAuth to be healthy again before continuing:

```bash
until curl -fs -o /dev/null http://localhost:9011/api/status; do sleep 3; done; echo "FusionAuth up"
```

Expected: `FusionAuth up` once the container finishes the kickstart (this can take 30–90s).

- [ ] **Step 2: Run the theme test and verify it passes**

```bash
latte test --test=org.lattejava.app.tests.FusionAuthThemeTest
```

Expected: **PASS** — the hosted login HTML now contains `/static/images/logo.svg` because the tenant Simple Theme is applied. If it still fails on the logo assertion, confirm the reset actually ran on a fresh volume (`docker compose ... down -v`, not just `restart`) and that `kickstart.json` parsed (Task 2 Step 4).

- [ ] **Step 3: Run the full suite to confirm no regressions**

The kickstart change touches shared FusionAuth state that every test logs in against, so run the whole suite (this also re-seeds D1 — same destructive note as `CLAUDE.md`'s test section; the developer already consented to the reset in Step 1, and the suite's own D1 wipe is normal `latte test` behavior):

```bash
latte test
```

Expected: **PASS** for the full suite, including the existing OIDC/login tests (`MainTest`, `VerificationFlowTest`) — proving the theme did not break the login flow, only restyled it.

- [ ] **Step 4: Final commit (only if the full suite required no fixes)**

If Step 3 surfaced no changes, there is nothing new to commit (Tasks 1–2 are already committed). If a fix was needed, commit it:

```bash
git add -A
git commit -m "fix: <describe the regression fix surfaced by the theme change>"
```

---

### Task 4: Fix the cross-origin block so the themed logo/favicons actually render

**Why:** Tasks 1–3 leave a true gap. FusionAuth renders the logo on its hosted login page
(origin `http://localhost:9011`) via `--img-logo: url('http://localhost:8080/static/images/logo.svg')`
— a cross-origin subresource. The Latte app's global `SecurityHeaders` emits
`Cross-Origin-Resource-Policy: same-origin` on every response, so the browser refuses to
deliver `/static` assets to the cross-origin FA page; the logo (and favicons) silently do
not render even though the URL is in the page. A `data:` URI is not an option — FA
validates `logoImageURL` and rejects `data:` with `400 [invalidURL]`.

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`
- Modify: `src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java`
- Dependency: requires the `web` module's `SecurityHeaders.defaults()`/`empty()` API
  (the `builder()` form was removed); `org.lattejava:web:0.2.0-{integration}` must be
  (re)published from the `web` module before the app build will compile.

- [ ] **Step 1: Add the failing contract test**

Add a second `@Test` to `FusionAuthThemeTest`, `staticAssetsAllowCrossOriginEmbedding`,
that GETs `http://localhost:{Main.PORT}/static/images/logo.svg` and asserts the response
header `Cross-Origin-Resource-Policy` equals `cross-origin`, and that GET `/` (a
non-static response) still has `Cross-Origin-Resource-Policy: same-origin`. (A string-only
"logo URL present in HTML" check is insufficient — it passes even when the browser would
block the image.)

- [ ] **Step 2: Migrate `Main` to the new `SecurityHeaders` API and run the test (red)**

Replace `SecurityHeaders.builder().contentSecurityPolicy(CSP_HEADER).build()` with
`SecurityHeaders.defaults().contentSecurityPolicy(CSP_HEADER)`. Run
`latte test --test=org.lattejava.app.tests.FusionAuthThemeTest`. Expected: the new method
**fails** (`/static` still `same-origin`); `hostedLoginPageUsesLatteTheme` passes; the app
compiled against the new `web` API.

- [ ] **Step 3: Add the scoped `/static` CORP override middleware**

In `Main.main()`, between `.install(oidc)` and `.baseDir(BASE_DIR).files("/static")`,
install a middleware that, when `request.getPath().startsWith("/static/")`, calls
`response.setHeader("Cross-Origin-Resource-Policy", "cross-origin")` then
`chain.next(request, response)`. It must be installed before `.files("/static")` (so it
runs before the static file handler) and the override must be unconditional `setHeader`
(the global `SecurityHeaders`, collected root-first, has already set `same-origin`, and
`SecurityHeaders.handle` only sets a header when unset).

- [ ] **Step 4: Verify green + full suite**

`latte test --test=org.lattejava.app.tests.FusionAuthThemeTest` → both pass. Then
`latte test` → full suite passes (the `Main` header-pipeline change touches every
response). This also fixes the favicons (same `/static` scope, same root cause) with no
kickstart change.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java src/test/java/org/lattejava/app/tests/FusionAuthThemeTest.java docs/
git commit -m "fix: serve /static cross-origin so the FusionAuth themed logo renders"
```

---

## Self-review

- **Spec coverage:**
  - Tenant-level Simple Theme, light, variables-only → Task 2 Steps 2–3 (theme `type: "simple"`, full variable map, tenant `themeId`).
  - Variable→token mapping table (branded rows + the FA-required `alert*`/`error*`/`*IconColor`/`monoFontColor` defaults + logo + favicons) → Task 2 Step 2 JSON (every spec row present, full required set; `logoImageSize` set to a concrete `200px`, the spec's "tuning detail left to implementation").
  - Kickstart wiring via fixed UUID + request blocks following the email-template pattern → Task 2 Steps 1–3.
  - Image-URL constraint (localhost, accepted) → documented in Background; encoded in the JSON.
  - Test asserting theme applied to hosted login page, preferring the logo-URL marker over a hex color (spec's brittleness note) → Task 1 (asserts `/static/images/logo.svg`, not a color).
  - Destructive kickstart re-apply requires confirmation (spec risk + project convention) → Task 3 Step 1.
- **Placeholder scan:** No TBD/TODO/"handle errors"/"similar to". Every code and JSON block is complete and literal. The only `<describe ...>` is inside an optional conditional commit message, not implementation content.
- **Type/name consistency:** `themeId` variable name, `#{themeId}` reference, the fixed UUID, `main.oidcConfig`, `Main.PORT`, `org.lattejava.app.tests.FusionAuthThemeTest`, and the `/static/images/logo.svg` marker are used identically across Tasks 1–3.
- **Scope:** Single subsystem (FA theme provisioning + one test). One plan, no decomposition needed.
