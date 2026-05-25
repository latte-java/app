# Groups Plan 01 — D1 Client + Schema + Migrations

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Cloudflare D1 data layer — schema migrations, a single `DatabaseClient` that talks to the D1 REST API, and revised model records that match the schema. Subsequent plans (GroupService, verification, membership flows, deletion, kickstart) build on this primitive.

**Architecture:** Each developer owns a Cloudflare D1 instance in their own account. Wrangler is used once-per-machine to apply migrations (`wrangler d1 migrations apply`); at runtime the JVM hits `https://api.cloudflare.com/.../d1/database/<id>/query` directly with a Bearer token. No Worker on the data path, in dev or prod. `DatabaseClient`'s public methods take/return model records (`Group`, `Member`, `GroupVerification`); SQL is internal.

**Tech Stack:** Java 25 (JPMS), restify 4.4.0 (HTTP+JSON via Jackson) for D1 REST calls, Cloudflare D1 (managed SQLite), Wrangler CLI (npm) for migrations, TestNG integration tests against real D1.

**Reference design:** `docs/design/2026-05-07-groups.md`

---

## Pre-flight (one-time, before running this plan)

Each developer needs:
1. A Cloudflare account.
2. A D1 database in that account (e.g. `latte-app-dev`).
3. An API token scoped `Account.D1:Edit`.
4. Wrangler installed (`npm install -D wrangler` — added by Task 1).
5. `~/.config/latte/app/config.properties` populated with `d1.accountId`, `d1.databaseId`, `d1.apiToken`, `d1.baseUrl=https://api.cloudflare.com/client/v4` (Task 4 wires the config keys).

This pre-flight is one-time; document in CLAUDE.md (Task 14).

---

## File Structure

**Create:**
- `wrangler.toml` — declares the D1 binding so `wrangler d1 migrations apply` and `wrangler d1 execute` work
- `migrations/0001_init.sql` — `groups`, `members`, `group_verifications` tables + indexes
- `migrations/0002_seed_reserved_groups.sql` — `INSERT OR IGNORE` for `org.lattejava`
- `src/main/java/org/lattejava/app/db/D1Config.java` — record holding `baseUrl`, `accountId`, `databaseId`, `apiToken`
- `src/main/java/org/lattejava/app/db/D1Exception.java` — runtime exception for non-2xx or `success=false` responses
- `src/main/java/org/lattejava/app/db/D1Request.java` — `{sql, params}` DTO
- `src/main/java/org/lattejava/app/db/D1Response.java` — `{success, errors, result}` DTO (with nested `D1Result` and `D1Meta`)
- `src/main/java/org/lattejava/app/db/D1Error.java` — `{code, message}` DTO inside response errors
- `src/main/java/org/lattejava/app/db/D1Result.java` — nested `{results, success, meta}` DTO
- `src/main/java/org/lattejava/app/db/DatabaseClient.java` — public API works on model records; private `query()` issues HTTP
- `src/main/java/org/lattejava/app/model/MembershipState.java` — enum `PENDING`, `ACTIVE`
- `src/main/java/org/lattejava/app/model/GroupVerification.java` — record matching `group_verifications` schema
- `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java` — integration tests against real D1

**Modify:**
- `package.json` — add `wrangler` dev-dependency
- `.gitignore` — add `.wrangler/` (wrangler local state) and `.dev.vars`
- `src/main/java/module-info.java` — `requires` Jackson + java.net.http; export `org.lattejava.app.db`
- `src/test/java/module-info.java` — `requires org.lattejava.app` already covers it; add `opens` for new test package
- `src/main/java/org/lattejava/app/Main.java` — load `d1.*` config keys, construct `DatabaseClient`
- `src/main/java/org/lattejava/app/model/Group.java` — schema-aligned shape (drop slug/domain/handleGroup/counts/etc.)
- `src/main/java/org/lattejava/app/model/Member.java` — schema-aligned shape
- `src/main/java/org/lattejava/app/model/GroupState.java` — drop `fromData()`; SQL stores enum names verbatim
- `src/main/java/org/lattejava/app/service/GroupService.java` — delete the body and leave a stub `listForUser` returning empty list (Plan 2 rewrites it)
- `src/main/java/org/lattejava/app/service/ViewService.java` — adapt to new `Group` shape (drop fields the templates won't have yet)
- `src/test/java/org/lattejava/app/tests/MainTest.java` — `@BeforeSuite` runs schema/data reset against D1 + seeds the test user owner row
- `src/test/java/org/lattejava/app/tests/GroupServiceTest.java` — adjust to the trimmed-down stub or delete if it asserts old behavior (Plan 2 rebuilds it)
- `src/test/resources/config.properties` — add placeholder `d1.*` keys
- `CLAUDE.md` — document D1 setup, migrations, dev test prerequisites

**Templates left alone for Plan 1.** `web/pages/groups/*.jte` and `web/components/group-list-row.jte` are static templates today; they'll be wired by Plan 2.

---

## Decisions locked in for this plan

- **HTTP client:** restify 4.4.0 (already a project dependency, idiomatic for this codebase, gives us typed JSON responses).
- **Test strategy:** integration tests against the developer's real D1 over the network. No mocking of D1.
- **Test isolation:** `@BeforeSuite` issues `DELETE FROM members; DELETE FROM groups; DELETE FROM group_verifications;` and re-inserts the seed group + the test user owner row.
- **Seed migration scope:** inserts only the `org.lattejava` group. The test user's owner row is inserted by the test fixture (since the FA test user UUID is generated at kickstart time and isn't known to a static SQL file). This is consistent with the design doc's "this design should not concern itself with these migration issues."
- **Enum string form:** SQL stores enum constant names verbatim — `VERIFIED`, `PENDING`, `FAILED`, `ACTIVE`, `OWNER`, `CONTRIBUTOR`. The design doc table examples show title-case (`Verified`, `Owner`); we deviate to keep `Enum.valueOf()` round-trip trivial. Templates can render whichever case they want via `state.name()` or a helper.
- **No batching/transactions in this plan.** Plan 3 (verification) needs an atomic `UPDATE state + DELETE verification`; we'll add a `batch()` method then.

---

## Task 1: Wrangler config + project housekeeping

**Files:**
- Create: `wrangler.toml`
- Modify: `package.json`
- Modify: `.gitignore`

- [ ] **Step 1: Create `wrangler.toml`**

```toml
name = "latte-app"
compatibility_date = "2026-05-01"

# Each developer overrides database_id via wrangler CLI flags or by editing
# this file locally. The committed value is a placeholder; CI/prod uses its own.
[[d1_databases]]
binding = "DB"
database_name = "latte-app-dev"
database_id = "00000000-0000-0000-0000-000000000000"
migrations_dir = "migrations"
```

- [ ] **Step 2: Add wrangler to `package.json`**

Edit `/Users/bpontarelli/dev/latte-java/app/package.json`. After the existing `"devDependencies"` block (or create it if absent), ensure it contains:

```json
{
  "private": true,
  "devDependencies": {
    "@tailwindcss/cli": "^4.0.0",
    "tailwindcss": "^4.0.0",
    "wrangler": "^3.78.0"
  }
}
```

(Preserve existing tailwind versions if different; only add the `wrangler` line.)

- [ ] **Step 3: Update `.gitignore`**

Append to `/Users/bpontarelli/dev/latte-java/app/.gitignore`:

```
.wrangler
.dev.vars
```

- [ ] **Step 4: Install wrangler locally**

Run: `cd /Users/bpontarelli/dev/latte-java/app && npm install`
Expected: `node_modules/wrangler` exists; no errors.

- [ ] **Step 5: Verify wrangler runs**

Run: `npx wrangler --version`
Expected: prints a 3.x version.

- [ ] **Step 6: Commit**

```bash
git add wrangler.toml package.json package-lock.json .gitignore
git commit -m "build: add wrangler for D1 migrations"
```

---

## Task 2: Initial schema migration

**Files:**
- Create: `migrations/0001_init.sql`

- [ ] **Step 1: Write the migration**

Create `/Users/bpontarelli/dev/latte-java/app/migrations/0001_init.sql`:

```sql
-- groups: keyed by reverse-DNS or short name. The name IS the identity.
CREATE TABLE groups (
  name              TEXT PRIMARY KEY,
  description       TEXT NOT NULL DEFAULT '',
  state             TEXT NOT NULL CHECK (state IN ('VERIFIED', 'PENDING', 'FAILED')),
  verification_code TEXT NULL,
  created_at        INTEGER NOT NULL,
  verified_at       INTEGER
);

-- members: group x user
CREATE TABLE members (
  group_name  TEXT    NOT NULL,
  user_id     TEXT    NOT NULL,
  role        TEXT    NOT NULL CHECK (role  IN ('OWNER', 'CONTRIBUTOR')),
  state       TEXT    NOT NULL CHECK (state IN ('PENDING', 'ACTIVE')),
  invited_by  TEXT,
  invited_at  INTEGER,
  joined_at   INTEGER,
  PRIMARY KEY (group_name, user_id),
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);

CREATE INDEX members_user_id_idx ON members(user_id);

-- group_verifications: one outstanding DNS TXT challenge per group
CREATE TABLE group_verifications (
  group_name      TEXT PRIMARY KEY,
  started_at      INTEGER NOT NULL,
  last_checked_at INTEGER,
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);
```

- [ ] **Step 2: Apply against your dev D1**

Run: `cd /Users/bpontarelli/dev/latte-java/app && npx wrangler d1 migrations apply latte-app-dev --remote`
Expected: `Migration applied: 0001_init.sql`. (If you get an auth error, run `npx wrangler login` first.)

- [ ] **Step 3: Verify tables exist**

Run: `npx wrangler d1 execute latte-app-dev --remote --command "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"`
Expected output contains `d1_migrations`, `group_verifications`, `groups`, `members`.

- [ ] **Step 4: Commit**

```bash
git add migrations/0001_init.sql
git commit -m "feat(db): initial schema for groups, members, verifications"
```

---

## Task 3: Reserved-group seed migration

**Files:**
- Create: `migrations/0002_seed_reserved_groups.sql`

- [ ] **Step 1: Write the seed migration**

Create `/Users/bpontarelli/dev/latte-java/app/migrations/0002_seed_reserved_groups.sql`:

```sql
-- Reserved group for the project itself. Verified state, no verification code.
-- The owner membership row is inserted by the test fixture at runtime, since
-- the FusionAuth test user UUID is generated at kickstart time.
INSERT OR IGNORE INTO groups (name, description, state, verification_code, created_at, verified_at)
VALUES (
  'org.lattejava',
  'Reserved group for the Latte Java project.',
  'VERIFIED',
  NULL,
  1714867200000,  -- 2024-05-05T00:00:00Z (arbitrary but stable)
  1714867200000
);
```

- [ ] **Step 2: Apply**

Run: `cd /Users/bpontarelli/dev/latte-java/app && npx wrangler d1 migrations apply latte-app-dev --remote`
Expected: `Migration applied: 0002_seed_reserved_groups.sql`.

- [ ] **Step 3: Verify the row**

Run: `npx wrangler d1 execute latte-app-dev --remote --command "SELECT name, state FROM groups"`
Expected: a single row `org.lattejava | VERIFIED`.

- [ ] **Step 4: Commit**

```bash
git add migrations/0002_seed_reserved_groups.sql
git commit -m "feat(db): seed reserved org.lattejava group"
```

---

## Task 4: Configuration plumbing for D1 keys

**Files:**
- Modify: `src/test/resources/config.properties`
- Modify: `src/main/java/org/lattejava/app/Main.java`

- [ ] **Step 1: Add placeholder D1 keys to test config**

Edit `/Users/bpontarelli/dev/latte-java/app/src/test/resources/config.properties` and append:

```properties
d1.baseUrl=https://api.cloudflare.com/client/v4
d1.accountId=replace-me
d1.databaseId=replace-me
d1.apiToken=replace-me
```

(Each developer's `~/.config/latte/app/config.properties` overrides these with real values per the layered-config mechanism.)

- [ ] **Step 2: Add D1 keys to required-config list in `Main.java`**

In `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/Main.java`, change the `Configuration` constructor's required-keys list. Locate:

```java
config = new Configuration(
    List.of("fusionauth.issuer",
            "fusionauth.baseUrl",
            "fusionauth.apiKey",
            "fusionauth.clientId",
            "fusionauth.clientSecret",
            "fusionauth.licenseKey"),
    Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
    Path.of("src/test/resources/config.properties")
);
```

Replace with:

```java
config = new Configuration(
    List.of("d1.accountId",
            "d1.apiToken",
            "d1.baseUrl",
            "d1.databaseId",
            "fusionauth.apiKey",
            "fusionauth.baseUrl",
            "fusionauth.clientId",
            "fusionauth.clientSecret",
            "fusionauth.issuer",
            "fusionauth.licenseKey"),
    Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
    Path.of("src/test/resources/config.properties")
);
```

(Alphabetized per `.claude/rules/code-conventions.md`.)

- [ ] **Step 3: Run tests to confirm config loads**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.MainTest`
Expected: PASS — same as before. The new keys are present (placeholders), nothing reads them yet.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/config.properties src/main/java/org/lattejava/app/Main.java
git commit -m "feat(config): require d1.* keys"
```

---

## Task 5: New + revised model records

**Files:**
- Create: `src/main/java/org/lattejava/app/model/MembershipState.java`
- Create: `src/main/java/org/lattejava/app/model/GroupVerification.java`
- Modify: `src/main/java/org/lattejava/app/model/Group.java`
- Modify: `src/main/java/org/lattejava/app/model/Member.java`
- Modify: `src/main/java/org/lattejava/app/model/GroupState.java`

- [ ] **Step 1: Write `MembershipState`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/model/MembershipState.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

public enum MembershipState {
  ACTIVE,
  PENDING
}
```

- [ ] **Step 2: Write `GroupVerification`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/model/GroupVerification.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record GroupVerification(
    String groupName,
    long startedAt,
    Long lastCheckedAt
) {
}
```

- [ ] **Step 3: Replace `Group` record**

Overwrite `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/model/Group.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

/**
 * A repository group. The {@code name} is the identity (no synthetic UUID).
 */
public record Group(
    String name,
    String description,
    GroupState state,
    String verificationCode,
    long createdAt,
    Long verifiedAt
) {
}
```

- [ ] **Step 4: Replace `Member` record**

Overwrite `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/model/Member.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record Member(
    String groupName,
    UUID userId,
    Role role,
    MembershipState state,
    UUID invitedBy,
    Long invitedAt,
    Long joinedAt
) {
}
```

- [ ] **Step 5: Strip `GroupState`**

Overwrite `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/model/GroupState.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

public enum GroupState {
  FAILED,
  PENDING,
  VERIFIED
}
```

- [ ] **Step 6: Patch consumers that no longer compile**

`GroupService.java`, `ViewService.java`, and `dashboard.jte` reference the old `Group` shape (`id`, `domain`, `viewerRole`, `artifactCount`, etc.). For Plan 1 we want the project to compile; Plan 2 rebuilds these properly.

Overwrite `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/GroupService.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.User;

/**
 * Stub. Plan 2 rewrites this against {@link org.lattejava.app.db.DatabaseClient}.
 */
public class GroupService {
  public List<Group> listForUser(User user) {
    return List.of();
  }
}
```

Read `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/ViewService.java` and any reference to old `Group` fields. Update `View` consumption accordingly. If `View` itself references old fields, simplify so the project compiles. (Use the Read tool to see the current contents before editing.)

If `web/pages/dashboard.jte` references removed `Group` fields, change those references to use only `name` and `state` for now. Plan 2 will rewire properly.

Delete `src/test/java/org/lattejava/app/tests/GroupServiceTest.java` (Plan 2 rebuilds it):

```bash
git rm src/test/java/org/lattejava/app/tests/GroupServiceTest.java
```

- [ ] **Step 7: Build to confirm everything compiles**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run tests**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: PASS. The dashboard test renders with the trimmed-down `Group` shape (sidebar will be empty since `GroupService.listForUser` returns empty list — adjust the `dashboard()` test assertion if it asserts `org.lattejava` shows in HTML).

If the `dashboard` test currently asserts `s.contains("org.lattejava")`, change that assertion to just `s.contains("<body")` (the sidebar-empty case). Plan 2 puts the assertion back when groups load from D1.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/lattejava/app/model src/main/java/org/lattejava/app/service src/test/java/org/lattejava/app/tests web/pages/dashboard.jte
git commit -m "refactor(model): align Group/Member with D1 schema; stub GroupService"
```

---

## Task 6: D1 DTOs (request, response, error, result)

**Files:**
- Create: `src/main/java/org/lattejava/app/db/D1Request.java`
- Create: `src/main/java/org/lattejava/app/db/D1Response.java`
- Create: `src/main/java/org/lattejava/app/db/D1Result.java`
- Create: `src/main/java/org/lattejava/app/db/D1Error.java`

These DTOs match the Cloudflare D1 REST API shape. Restify (via Jackson) deserializes responses into them.

- [ ] **Step 1: `D1Request`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

public record D1Request(String sql, List<Object> params) {
}
```

- [ ] **Step 2: `D1Error`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

public record D1Error(int code, String message) {
}
```

- [ ] **Step 3: `D1Result`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

public record D1Result(
    List<Map<String, Object>> results,
    boolean success
) {
}
```

(We ignore the `meta` block — duration/rows-read aren't needed by the app. Jackson with `FAIL_ON_UNKNOWN_PROPERTIES=false` skips unknown fields; restify configures this by default.)

- [ ] **Step 4: `D1Response`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

public record D1Response(
    boolean success,
    List<D1Error> errors,
    List<D1Result> result
) {
}
```

- [ ] **Step 5: Build to verify**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/db/D1Request.java src/main/java/org/lattejava/app/db/D1Response.java src/main/java/org/lattejava/app/db/D1Result.java src/main/java/org/lattejava/app/db/D1Error.java
git commit -m "feat(db): D1 REST API DTOs"
```

---

## Task 7: D1Config + D1Exception + module exports

**Files:**
- Create: `src/main/java/org/lattejava/app/db/D1Config.java`
- Create: `src/main/java/org/lattejava/app/db/D1Exception.java`
- Modify: `src/main/java/module-info.java`

- [ ] **Step 1: `D1Config`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

public record D1Config(
    String accountId,
    String apiToken,
    String baseUrl,
    String databaseId
) {
  public String queryUrl() {
    return baseUrl + "/accounts/" + accountId + "/d1/database/" + databaseId + "/query";
  }
}
```

- [ ] **Step 2: `D1Exception`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

public class D1Exception extends RuntimeException {
  public D1Exception(String message) {
    super(message);
  }

  public D1Exception(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 3: Export the `db` package**

Edit `/Users/bpontarelli/dev/latte-java/app/src/main/java/module-info.java`. Replace its body so exports/requires stay alphabetized:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.app {
  requires fusionauth.java.client;
  requires gg.jte;
  requires gg.jte.runtime;
  requires org.lattejava.http;
  requires org.lattejava.jwt;
  requires org.lattejava.web;
  requires restify;

  exports org.lattejava.app;
  exports org.lattejava.app.db;
  exports org.lattejava.app.model;
  exports org.lattejava.app.service;
  exports org.lattejava.app.util;
}
```

- [ ] **Step 4: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/D1Config.java src/main/java/org/lattejava/app/db/D1Exception.java src/main/java/module-info.java
git commit -m "feat(db): D1Config + module export of db package"
```

---

## Task 8: DatabaseClient skeleton + private query()

**Files:**
- Create: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Create: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

This task wires up the HTTP layer end-to-end and proves the pipe works with a `SELECT 1` round-trip. No model methods yet — those land in Tasks 9–11.

- [ ] **Step 1: Write the failing test**

Create `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.db;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import org.lattejava.app.db.D1Config;
import org.lattejava.app.db.D1Response;
import org.lattejava.app.db.DatabaseClient;
import org.lattejava.web.Configuration;

@Test
public class DatabaseClientTest {
  public DatabaseClient client;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    D1Config d1 = new D1Config(
        config.get("d1.accountId"),
        config.get("d1.apiToken"),
        config.get("d1.baseUrl"),
        config.get("d1.databaseId")
    );
    client = new DatabaseClient(d1);
  }

  @Test
  public void selectOne() {
    D1Response response = client.query("SELECT 1 AS one");
    assertTrue(response.success(), "D1 query should succeed");
    assertEquals(response.result().getFirst().results().getFirst().get("one"), 1);
  }
}
```

Add the test package to the test module. Edit `/Users/bpontarelli/dev/latte-java/app/src/test/java/module-info.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.app.tests {
  requires fusionauth.java.client;
  requires java.net.http;
  requires org.lattejava.app;
  requires org.lattejava.http;
  requires org.lattejava.web;
  requires org.testng;
  requires restify;

  opens org.lattejava.app.tests to org.testng;
  opens org.lattejava.app.tests.db to org.testng;
}
```

- [ ] **Step 2: Run the test, expect compilation failure**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL with `cannot find symbol DatabaseClient.query` (class doesn't exist yet).

- [ ] **Step 3: Implement `DatabaseClient` (skeleton + `query`)**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/db/DatabaseClient.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;
import module restify;

public class DatabaseClient {
  private final D1Config config;

  public DatabaseClient(D1Config config) {
    this.config = config;
  }

  public D1Response query(String sql, Object... params) {
    D1Request body = new D1Request(sql, List.of(params));
    ClientResponse<D1Response, D1Response> response = new RESTClient<>(D1Response.class, D1Response.class)
        .url(config.queryUrl())
        .header("Authorization", "Bearer " + config.apiToken())
        .header("Content-Type", "application/json")
        .bodyHandler(new JSONBodyHandler(body))
        .successResponseHandler(new JSONResponseHandler<>(D1Response.class))
        .errorResponseHandler(new JSONResponseHandler<>(D1Response.class))
        .post()
        .go();

    if (response.exception != null) {
      throw new D1Exception("D1 request failed for SQL [" + sql + "]", response.exception);
    }
    D1Response payload = response.wasSuccessful() ? response.successResponse : response.errorResponse;
    if (payload == null || !payload.success()) {
      String detail = payload == null
          ? "no response body"
          : payload.errors().stream().map(D1Error::message).collect(Collectors.joining("; "));
      throw new D1Exception("D1 query failed [" + sql + "]: [" + detail + "]");
    }
    return payload;
  }
}
```

(Note the error-message bracket convention from `.claude/rules/error-messages.md`.)

- [ ] **Step 4: Run the test, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: PASS — the test hits real D1 and round-trips `SELECT 1`. If you see a 401, your `~/.config/latte/app/config.properties` `d1.apiToken` is wrong. If you see "database not found," your `d1.databaseId` doesn't match.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java src/test/java/module-info.java
git commit -m "feat(db): DatabaseClient with raw query()"
```

---

## Task 9: DatabaseClient — groups CRUD

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing tests**

Append to `DatabaseClientTest.java` inside the class body:

```java
@Test
public void insertAndFindGroup() {
  Group g = new Group("test.example.fixture", "fixture", GroupState.PENDING, "code-abc", 1714867200000L, null);
  client.insertGroup(g);
  try {
    Optional<Group> found = client.findGroup("test.example.fixture");
    assertTrue(found.isPresent());
    assertEquals(found.get().name(), "test.example.fixture");
    assertEquals(found.get().description(), "fixture");
    assertEquals(found.get().state(), GroupState.PENDING);
    assertEquals(found.get().verificationCode(), "code-abc");
    assertEquals(found.get().createdAt(), 1714867200000L);
    assertNull(found.get().verifiedAt());
  } finally {
    client.deleteGroup("test.example.fixture");
  }
}

@Test
public void findGroupAbsent() {
  assertTrue(client.findGroup("does.not.exist.fixture").isEmpty());
}

@Test
public void deleteGroupRemoves() {
  Group g = new Group("test.delete.fixture", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
  client.insertGroup(g);
  client.deleteGroup("test.delete.fixture");
  assertTrue(client.findGroup("test.delete.fixture").isEmpty());
}
```

Add to the imports at the top of the file:

```java
import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
```

- [ ] **Step 2: Run tests, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL with `cannot find symbol insertGroup`.

- [ ] **Step 3: Implement `insertGroup`, `findGroup`, `deleteGroup`**

Add to `DatabaseClient.java` (place these methods alphabetically among public methods):

```java
public void deleteGroup(String name) {
  query("DELETE FROM groups WHERE name = ?", name);
}

public Optional<Group> findGroup(String name) {
  D1Response response = query("SELECT name, description, state, verification_code, created_at, verified_at FROM groups WHERE name = ?", name);
  List<Map<String, Object>> rows = response.result().getFirst().results();
  if (rows.isEmpty()) {
    return Optional.empty();
  }
  return Optional.of(rowToGroup(rows.getFirst()));
}

public void insertGroup(Group group) {
  query(
      "INSERT INTO groups (name, description, state, verification_code, created_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
      group.name(),
      group.description(),
      group.state().name(),
      group.verificationCode(),
      group.createdAt(),
      group.verifiedAt()
  );
}

private static Group rowToGroup(Map<String, Object> row) {
  return new Group(
      (String) row.get("name"),
      (String) row.get("description"),
      GroupState.valueOf((String) row.get("state")),
      (String) row.get("verification_code"),
      ((Number) row.get("created_at")).longValue(),
      row.get("verified_at") == null ? null : ((Number) row.get("verified_at")).longValue()
  );
}
```

Also add to the imports at the top of `DatabaseClient.java`:

```java
import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
```

- [ ] **Step 4: Run tests, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: PASS for all four methods (selectOne + the three new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): groups CRUD on DatabaseClient"
```

---

## Task 10: DatabaseClient — members CRUD

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing tests**

Append to `DatabaseClientTest.java`:

```java
@Test
public void insertAndDeleteMember() {
  UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  UUID inviter = UUID.fromString("22222222-2222-2222-2222-222222222222");
  Group g = new Group("test.member.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    Member m = new Member("test.member.fixture", userId, Role.OWNER, MembershipState.PENDING, inviter, 1714867200001L, null);
    client.insertMember(m);
    client.deleteMember("test.member.fixture", userId);
    // No find method yet (Plan 2 adds list/find on members); a successful delete
    // round-trip with no exception is the assertion.
  } finally {
    client.deleteGroup("test.member.fixture");
  }
}
```

Add imports:

```java
import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.Role;
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL — `insertMember`/`deleteMember` don't exist.

- [ ] **Step 3: Implement methods**

Add to `DatabaseClient.java`:

```java
public void deleteMember(String groupName, UUID userId) {
  query("DELETE FROM members WHERE group_name = ? AND user_id = ?", groupName, userId.toString());
}

public void insertMember(Member member) {
  query(
      "INSERT INTO members (group_name, user_id, role, state, invited_by, invited_at, joined_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      member.groupName(),
      member.userId().toString(),
      member.role().name(),
      member.state().name(),
      member.invitedBy() == null ? null : member.invitedBy().toString(),
      member.invitedAt(),
      member.joinedAt()
  );
}
```

Add imports:

```java
import org.lattejava.app.model.Member;
import org.lattejava.app.model.Role;
```

(`MembershipState` isn't directly referenced in `DatabaseClient`, but `Role` is via `member.role()`.)

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): members insert/delete on DatabaseClient"
```

---

## Task 11: DatabaseClient — verifications CRUD

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing test**

Append to `DatabaseClientTest.java`:

```java
@Test
public void insertAndDeleteVerification() {
  Group g = new Group("test.verify.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    GroupVerification v = new GroupVerification("test.verify.fixture", 1714867200002L, 1714867200002L);
    client.insertVerification(v);
    client.deleteVerification("test.verify.fixture");
  } finally {
    client.deleteGroup("test.verify.fixture");
  }
}
```

Add import:

```java
import org.lattejava.app.model.GroupVerification;
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL — methods don't exist.

- [ ] **Step 3: Implement methods**

Add to `DatabaseClient.java`:

```java
public void deleteVerification(String groupName) {
  query("DELETE FROM group_verifications WHERE group_name = ?", groupName);
}

public void insertVerification(GroupVerification verification) {
  query(
      "INSERT INTO group_verifications (group_name, started_at, last_checked_at) VALUES (?, ?, ?)",
      verification.groupName(),
      verification.startedAt(),
      verification.lastCheckedAt()
  );
}
```

Add import:

```java
import org.lattejava.app.model.GroupVerification;
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): group_verifications insert/delete on DatabaseClient"
```

---

## Task 12: Wire DatabaseClient into Main

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`

- [ ] **Step 1: Construct `DatabaseClient` in `Main`**

Edit `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/Main.java`. Add import for the new package:

```java
import module org.lattejava.app;
```

(That module-import already covers `org.lattejava.app.db.*` because the package is exported.)

In the field block (alphabetized by visibility per code conventions), add:

```java
public final DatabaseClient databaseClient;
public final D1Config d1Config;
```

In the constructor body, after the existing config load and before `oidcConfig`:

```java
d1Config = new D1Config(
    config.get("d1.accountId"),
    config.get("d1.apiToken"),
    config.get("d1.baseUrl"),
    config.get("d1.databaseId")
);
databaseClient = new DatabaseClient(d1Config);
```

The full updated `Main.java` field section should look like:

```java
public final Configuration config;
public final DatabaseClient databaseClient;
public final D1Config d1Config;
public final FusionAuthClient fusionAuth;
public final GroupService groupService;
public final OIDC<User> oidc;
public final OIDCConfig oidcConfig;
public final JTETemplates templates;
public final ViewService viewService;
public final Web web;
```

- [ ] **Step 2: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Boot test**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: PASS — `Main` constructs successfully, `databaseClient` is non-null. (The `dashboard` test's HTML assertion still uses the trimmed assertion from Task 5.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(db): construct DatabaseClient in Main"
```

---

## Task 13: Test fixture — BeforeSuite reset + seed test-user owner

**Files:**
- Modify: `src/test/java/org/lattejava/app/tests/MainTest.java`

We want every test run to start from a known DB state: only the seeded `org.lattejava` group, plus an `OWNER` membership for the FA test user (whose UUID is discovered at runtime).

- [ ] **Step 1: Add reset + seed to `MainTest.beforeSuite()`**

In `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/MainTest.java`, replace `beforeSuite()` with:

```java
@BeforeSuite
public void beforeSuite() {
  main = new Main();
  main.main();

  resetAndSeedDatabase();

  oidc = new OIDCTestFixture(test, main.oidcConfig);
}

private void resetAndSeedDatabase() {
  // Wipe everything; cascades drop members + verifications.
  main.databaseClient.query("DELETE FROM group_verifications");
  main.databaseClient.query("DELETE FROM members");
  main.databaseClient.query("DELETE FROM groups");

  // Re-insert the reserved group (the migration would have, but DELETE wiped it).
  main.databaseClient.query(
      "INSERT INTO groups (name, description, state, verification_code, created_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
      "org.lattejava",
      "Reserved group for the Latte Java project.",
      "VERIFIED",
      null,
      1714867200000L,
      1714867200000L
  );

  // Discover the FA test user UUID and seed an OWNER membership.
  ClientResponse<UserResponse, ?> userResponse =
      main.fusionAuth.retrieveUserByEmail("test@lattejava.org");
  if (!userResponse.wasSuccessful() || userResponse.successResponse.user == null) {
    throw new IllegalStateException("FA test user not found - is FusionAuth running with kickstart applied?");
  }
  UUID testUserId = userResponse.successResponse.user.id;
  main.databaseClient.query(
      "INSERT INTO members (group_name, user_id, role, state, invited_by, invited_at, joined_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
      "org.lattejava",
      testUserId.toString(),
      "OWNER",
      "ACTIVE",
      null,
      null,
      1714867200000L
  );
}
```

Add the necessary imports (top of file):

```java
import module fusionauth.java.client;
import module java.base;
```

- [ ] **Step 2: Build + run all tests**

Run: `latte test`
Expected: PASS for `MainTest` (reset + seed runs cleanly) and `DatabaseClientTest` (still passes; its tests use disjoint fixture group names so they don't collide with the seed).

- [ ] **Step 3: Re-run tests to confirm idempotency**

Run: `latte test`
Expected: PASS again. The reset wipes the prior seed and re-creates it, so consecutive runs are stable.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "test: reset and seed D1 in BeforeSuite"
```

---

## Task 14: Document D1 setup in CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add a "Database (D1)" section**

In `/Users/bpontarelli/dev/latte-java/app/CLAUDE.md`, after the `## Build & run` section and before `## Architecture`, insert:

```markdown
## Database (D1)

Production and dev both run against Cloudflare D1 over the REST API. **Each developer needs their own D1 database in their own Cloudflare account** — there is no local emulator.

### One-time setup

1. Create a Cloudflare D1 database (Cloudflare dashboard → Workers & Pages → D1 → Create). Name it whatever; the committed default in `wrangler.toml` is `latte-app-dev`.
2. Create an API token (User → API Tokens → Create) with `Account.D1:Edit` scope.
3. Add to `~/.config/latte/app/config.properties`:

   ```properties
   d1.baseUrl=https://api.cloudflare.com/client/v4
   d1.accountId=<your account id>
   d1.databaseId=<your database id>
   d1.apiToken=<your api token>
   ```
4. Apply migrations:

   ```
   cd app
   npx wrangler d1 migrations apply latte-app-dev --remote
   ```

   (Replace `latte-app-dev` with your DB name. Re-run after adding new migrations.)

### Migrations

Schema changes are SQL files in `migrations/` numbered `NNNN_description.sql`. Wrangler tracks applied migrations in the built-in `d1_migrations` table. Apply with `npx wrangler d1 migrations apply <db-name> --remote`.

### Tests + D1

`latte test` requires:

- FusionAuth running locally on `:9013` (existing requirement).
- Network access to your D1 (the test fixture issues `DELETE`/`INSERT` against the real DB before the suite runs).

`MainTest.beforeSuite()` wipes all rows and re-seeds the `org.lattejava` group + an `OWNER` membership for the FA test user.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: D1 setup and test prerequisites"
```

---

## Self-review checklist

- Schema in 0001 matches the design doc's schema block (column names, types, defaults, FKs, the `members_user_id_idx`).
- 0002 is idempotent (`INSERT OR IGNORE`).
- `D1Config.queryUrl()` produces `https://api.cloudflare.com/client/v4/accounts/<id>/d1/database/<id>/query`.
- `DatabaseClient.query` is the single HTTP entry point; all CRUD methods route through it.
- All public methods on `DatabaseClient` take/return model records — no raw `Map<String, Object>` leaks past the `rowToGroup`-style internal helpers.
- `Group`, `Member`, `GroupVerification`, `MembershipState`, `Role`, `GroupState` match the schema columns and the design doc enum values (uppercase in SQL).
- Module exports include `org.lattejava.app.db`.
- Required config keys are alphabetized.
- TestNG fixture seeds the FA test user owner membership at runtime via `retrieveUserByEmail`.
- Errors thrown by `DatabaseClient` use `[value]` bracket convention (`.claude/rules/error-messages.md`).
- Copyright headers present on every new `.java` file (`.claude/rules/copyright.md`).
- No mocks of D1 — tests run against the real DB.

---

## What this plan deliberately does NOT do (deferred to later plans)

- **Plan 02 — GroupService rewrite:** validation rules (TLD list loader, dot-boundary subgroup check, short-name vs reverse-DNS), creation flow, listing groups for a user, prefix collision query, sidebar wiring on dashboard, restoring `groups/list.jte` and `groups/new.jte` against real data.
- **Plan 03 — Verification:** DNS background task, GitHub OAuth flow, retry button, `verified_at` writes, atomic state-update + verification-row-delete (introduces `batch()` on `DatabaseClient`).
- **Plan 04 — Membership flows:** invite (with new-user/existing-user split + email templates), accept/decline, remove, change role, leave; UI for `members.jte`, `role-picker.jte`, accept-invitation card on `overview.jte`.
- **Plan 05 — Deletion:** R2 emptiness check via S3 SDK, `Settings` tab wiring, `DELETE` cascade verification.
- **Plan 06 — Kickstart:** SMTP config (SendGrid via env-var password), GitHub IDP with `read:org`, the two new email templates, removal of FA Group EntityType + grants.

Plan 1 is "data layer working, Main constructs cleanly, tests pass." Anything that asserts feature-level behavior is in Plan 02+.
