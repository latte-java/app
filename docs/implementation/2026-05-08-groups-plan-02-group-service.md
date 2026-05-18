# Groups Plan 02 — GroupService Rewrite + Creation/Validation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stub `GroupService` with a real D1-backed implementation: validate group names against the IANA TLD list, Java identifier rules, length, and prefix-collision; list a user's groups for the dashboard sidebar; create new groups (short-name auto-VERIFIED, reverse-DNS PENDING with a verification code, github.io reverse-DNS PENDING with no code); seed an OWNER membership for the creator. Wire routes for `/app/groups`, `/app/groups/new`, and `POST /app/groups/new`.

**Architecture:** Validation is a pure `GroupValidator` class that depends on `DatabaseClient` (for uniqueness + ancestor checks) and `TLDList` (for first-label TLD lookup). `TLDList` is loaded once at app startup from the IANA list. `GroupService` orchestrates validation + insert + verification-row + OWNER membership across multiple `DatabaseClient` calls. Verification mechanics (DNS scanner, GitHub OAuth) are deferred to Plan 03; this plan only sets the initial state and verification code.

**Tech Stack:** Java 25 (JPMS), `DatabaseClient` over Cloudflare D1, JDK `HttpClient` for the IANA fetch, JTE templates already scaffolded in `web/pages/groups/`, TestNG integration tests against real D1 + FusionAuth.

**Reference design:** `docs/design/2026-05-07-groups.md` (Creating a new group, validation rules, schema).

**Plan 01 outcome to build on:** `DatabaseClient` exists with raw `query()` plus CRUD on groups/members/verifications. `D1Tools` provides typed row-extraction helpers. `GroupService` is a stub returning `List.of()`. The `org.lattejava.app.db` package is exported. Models match the schema (UPPERCASE enum strings).

---

## Pre-flight

Same as Plan 01: each developer needs their own D1 + FusionAuth running locally. No new infra in this plan.

---

## File Structure

**Create:**
- `src/main/java/org/lattejava/app/service/GroupValidator.java` — pure validation; depends on `DatabaseClient` + `TLDList`.
- `src/main/java/org/lattejava/app/service/GroupValidation.java` — record carrying validation errors and a `valid()` flag.
- `src/main/java/org/lattejava/app/service/validation/ValidationException.java` — thrown by `GroupService.create` when validation fails.
- `src/main/java/org/lattejava/app/service/GroupKind.java` — enum: `SHORT_NAME`, `REVERSE_DNS`, `REVERSE_DNS_GITHUB`.
- `src/main/java/org/lattejava/app/util/TLDList.java` — loads + caches IANA TLD list.
- `src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java` — unit tests against real D1.
- `src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java` — integration tests for `listForUser` and `create`.

**Modify:**
- `src/main/java/org/lattejava/app/db/DatabaseClient.java` — add `findAncestorGroup`, `listGroupsForUser`.
- `src/main/java/org/lattejava/app/service/GroupService.java` — replace stub with real impl (depends on `DatabaseClient`, `GroupValidator`).
- `src/main/java/org/lattejava/app/Main.java` — construct `TLDList`, `GroupValidator`, `GroupService(databaseClient, groupValidator)`; add `/app/groups` route prefix.
- `src/test/java/org/lattejava/app/tests/MainTest.java` — restore the `org.lattejava` assertion in the `dashboard` test.
- `src/test/java/module-info.java` — `opens org.lattejava.app.tests.service to org.testng;`.
- `web/layout/sidebar.jte` — hardcoded paths (`/groups`, `/activity`, `/`, etc.) prefixed with `/app`.
- `web/pages/groups/list.jte` — form action / hrefs prefixed with `/app`.
- `web/pages/groups/new.jte` — form action / hrefs prefixed with `/app`.

**Templates not touched in this plan:** `groups/detail.jte`, `groups/overview.jte`, `groups/members.jte`, `groups/settings.jte`, `groups/verify.jte`, `groups/artifacts.jte`. Plan 03+ wire those.

---

## Decisions locked in for this plan

- **Verification code format:** 32 lowercase hex chars (16 bytes from `SecureRandom`), generated as a private static helper in `GroupService`.
- **TLD list:** download once at startup from `https://data.iana.org/TLD/tlds-alpha-by-domain.txt` via JDK `HttpClient` with a 30 s timeout. If the download fails, the app fails to start (loud failure beats silent staleness). Tests can construct a `TLDList` directly from a `Set<String>`.
- **Validation result:** `GroupValidation(List<String> errors)` record. `valid()` returns `errors.isEmpty()`. The error list is plain English with bracketed runtime values (e.g. `"Group name [foo.bar] starts with [foo] which is not a TLD."`). Form re-render iterates the list.
- **`GroupKind`:** computed as a separate static helper after validation passes (`GroupValidator.kindOf(String)`). Determines the creation path: `SHORT_NAME` → state `VERIFIED`, no verification code, no verifications row; `REVERSE_DNS_GITHUB` → state `PENDING`, no code, no verifications row; `REVERSE_DNS` → state `PENDING`, with code, with verifications row.
- **Creator → OWNER membership:** inserted with `state=ACTIVE`, `joined_at=now`, `invited_by=null`, `invited_at=null`.
- **Form `kind` radio:** ignored for validation. The validator inspects the actual name. UI may or may not match the chosen kind; the validator gives the authoritative answer.
- **Prefix-collision query:** explicit dot-boundary prefix lookup via `IN (?, ?, ...)` over precomputed prefixes. Plan 03+ may switch to LIKE-based when scale matters.
- **No `GroupServiceTest` mocks.** Tests hit real D1 and use `test.*` fixture group names that don't collide with the seeded `org.lattejava`.

---

## Task 1: DatabaseClient — `listGroupsForUser` + `findAncestorGroup`

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing tests**

Append to `DatabaseClientTest.java`:

```java
@Test
public void listGroupsForUser_returnsActiveAndPendingMemberships() {
  UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
  Group g1 = new Group("test.list.one", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
  Group g2 = new Group("test.list.two", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g1);
  client.insertGroup(g2);
  try {
    client.insertMember(new Member("test.list.one", userId, Role.OWNER, MembershipState.ACTIVE, null, null, 1714867200001L));
    client.insertMember(new Member("test.list.two", userId, Role.CONTRIBUTOR, MembershipState.PENDING, userId, 1714867200002L, null));

    List<Group> groups = client.listGroupsForUser(userId);
    assertEquals(groups.size(), 2);
    assertTrue(groups.stream().anyMatch(g -> g.name().equals("test.list.one")));
    assertTrue(groups.stream().anyMatch(g -> g.name().equals("test.list.two")));
  } finally {
    client.deleteGroup("test.list.one");
    client.deleteGroup("test.list.two");
  }
}

@Test
public void listGroupsForUser_emptyForUnknown() {
  UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
  assertTrue(client.listGroupsForUser(userId).isEmpty());
}

@Test
public void findAncestorGroup_findsExactPrefix() {
  Group parent = new Group("test.ancestor.parent", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
  client.insertGroup(parent);
  try {
    Optional<Group> found = client.findAncestorGroup("test.ancestor.parent.child");
    assertTrue(found.isPresent());
    assertEquals(found.get().name(), "test.ancestor.parent");
  } finally {
    client.deleteGroup("test.ancestor.parent");
  }
}

@Test
public void findAncestorGroup_emptyForShortName() {
  assertTrue(client.findAncestorGroup("just_a_handle").isEmpty());
}

@Test
public void findAncestorGroup_doesNotMatchPartialLabels() {
  Group g = new Group("test.examples", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
  client.insertGroup(g);
  try {
    // 'test.example.foo' must NOT match 'test.examples' — labels must align on dots.
    assertTrue(client.findAncestorGroup("test.example.foo").isEmpty());
  } finally {
    client.deleteGroup("test.examples");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL with `cannot find symbol listGroupsForUser` and `findAncestorGroup`.

- [ ] **Step 3: Implement `listGroupsForUser` and `findAncestorGroup`**

Add to `DatabaseClient.java`. Place alphabetically:
- `findAncestorGroup` between `deleteVerification` and `findGroup`
- `listGroupsForUser` between `insertVerification` and `query`

```java
public Optional<Group> findAncestorGroup(String name) {
  String[] labels = name.split("\\.");
  if (labels.length < 2) {
    return Optional.empty();
  }
  List<String> prefixes = new ArrayList<>();
  for (int i = 1; i < labels.length; i++) {
    prefixes.add(String.join(".", Arrays.copyOfRange(labels, 0, i)));
  }
  String placeholders = String.join(",", Collections.nCopies(prefixes.size(), "?"));
  D1Response response = query(
      "SELECT name, description, state, verification_code, created_at, verified_at FROM groups WHERE name IN (" + placeholders + ") LIMIT 1",
      prefixes.toArray()
  );
  List<Map<String, Object>> rows = response.result().getFirst().results();
  if (rows.isEmpty()) {
    return Optional.empty();
  }
  return Optional.of(rowToGroup(rows.getFirst()));
}

public List<Group> listGroupsForUser(UUID userId) {
  D1Response response = query(
      "SELECT g.name, g.description, g.state, g.verification_code, g.created_at, g.verified_at "
          + "FROM groups g JOIN members m ON m.group_name = g.name WHERE m.user_id = ?",
      userId.toString()
  );
  List<Map<String, Object>> rows = response.result().getFirst().results();
  List<Group> groups = new ArrayList<>(rows.size());
  for (Map<String, Object> row : rows) {
    groups.add(rowToGroup(row));
  }
  return groups;
}
```

(`Arrays`, `Collections`, `ArrayList` are in `java.base` — covered by `import module java.base`.)

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: 11/11 PASS (the prior 6 + 5 new tests).

- [ ] **Step 5: Run MainTest**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: 4/4 PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java
git add src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): listGroupsForUser + findAncestorGroup on DatabaseClient"
```

---

## Task 2: TLDList class

**Files:**
- Create: `src/main/java/org/lattejava/app/util/TLDList.java`
- Test: deferred to GroupValidator tests in Task 4 (TLDList is small enough that a separate test class is overkill)

- [ ] **Step 1: Create `TLDList.java`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/util/TLDList.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.util;

import module java.base;
import module java.net.http;

/**
 * Cached IANA top-level-domain list. Loaded once at startup; only a JVM restart reloads.
 */
public class TLDList {
  public static final String IANA_URL = "https://data.iana.org/TLD/tlds-alpha-by-domain.txt";
  private final Set<String> tlds;

  public TLDList(Set<String> tlds) {
    this.tlds = Set.copyOf(tlds);
  }

  public static TLDList fromIana() {
    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder(URI.create(IANA_URL))
          .timeout(Duration.ofSeconds(30))
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException("IANA TLD fetch returned HTTP [" + response.statusCode() + "]");
      }
      return fromText(response.body());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to download IANA TLD list from [" + IANA_URL + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while downloading IANA TLD list", e);
    }
  }

  public static TLDList fromText(String content) {
    Set<String> tlds = content.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank() && !line.startsWith("#"))
        .map(line -> line.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    return new TLDList(tlds);
  }

  public boolean contains(String tld) {
    return tld != null && tlds.contains(tld.toLowerCase(Locale.ROOT));
  }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/util/TLDList.java
git commit -m "feat(util): TLDList loader for IANA TLD list"
```

---

## Task 3: GroupKind enum + GroupValidation record + ValidationException

**Files:**
- Create: `src/main/java/org/lattejava/app/service/GroupKind.java`
- Create: `src/main/java/org/lattejava/app/service/GroupValidation.java`
- Create: `src/main/java/org/lattejava/app/service/validation/ValidationException.java`

- [ ] **Step 1: Create `GroupKind.java`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

public enum GroupKind {
  REVERSE_DNS,
  REVERSE_DNS_GITHUB,
  SHORT_NAME
}
```

- [ ] **Step 2: Create `GroupValidation.java`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;

public record GroupValidation(List<String> errors) {
  public boolean valid() {
    return errors.isEmpty();
  }
}
```

- [ ] **Step 3: Create `ValidationException.java`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import module java.base;

public class ValidationException extends RuntimeException {
  private final List<String> errors;

  public ValidationException(List<String> errors) {
    super("Group validation failed: [" + String.join("; ", errors) + "]");
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}
```

- [ ] **Step 4: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/GroupKind.java
git add src/main/java/org/lattejava/app/service/GroupValidation.java
git add src/main/java/org/lattejava/app/service/validation/ValidationException.java
git commit -m "feat(service): GroupKind + GroupValidation + ValidationException"
```

---

## Task 4: GroupValidator — pure validation rules

**Files:**
- Create: `src/main/java/org/lattejava/app/service/GroupValidator.java`
- Create: `src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java`
- Modify: `src/test/java/module-info.java` (`opens` for new test package)

- [ ] **Step 1: Update test `module-info.java`**

Edit `/Users/bpontarelli/dev/latte-java/app/src/test/java/module-info.java` to add the new opens (alphabetized):

```java
opens org.lattejava.app.tests to org.testng;
opens org.lattejava.app.tests.db to org.testng;
opens org.lattejava.app.tests.service to org.testng;
```

- [ ] **Step 2: Write failing test**

Create `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.lattejava.app.service.validation.*;
import org.lattejava.web.Configuration;

@Test
public class GroupValidatorTest {
  public DatabaseClient client;
  public GroupValidator validator;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    TLDList tlds = new TLDList(Set.of("org", "com", "io", "dev", "net"));
    validator = new GroupValidator(client, tlds);
  }

  @Test
  public void validReverseDNS() {
    assertTrue(validator.validate("org.example").valid());
  }

  @Test
  public void validShortName() {
    assertTrue(validator.validate("my_handle").valid());
  }

  @Test
  public void rejectsForwardDNS() {
    GroupValidation result = validator.validate("example.org");
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("[example]")), result.errors().toString());
  }

  @Test
  public void rejectsTooLong() {
    String tooLong = "a".repeat(256);
    GroupValidation result = validator.validate(tooLong);
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).contains("length")));
  }

  @Test
  public void rejectsNonAscii() {
    GroupValidation result = validator.validate("éclair");
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).contains("ascii")));
  }

  @Test
  public void rejectsInvalidJavaIdentifier() {
    GroupValidation result = validator.validate("org.1example");
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("[1example]")));
  }

  @Test
  public void rejectsJavaKeyword() {
    GroupValidation result = validator.validate("org.class");
    assertFalse(result.valid());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("[class]")));
  }

  @Test
  public void rejectsLeadingDot() {
    GroupValidation result = validator.validate(".org.example");
    assertFalse(result.valid());
  }

  @Test
  public void rejectsConsecutiveDots() {
    GroupValidation result = validator.validate("org..example");
    assertFalse(result.valid());
  }

  @Test
  public void rejectsExistingName() {
    Group existing = new Group("test.exists.fixture", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
    client.insertGroup(existing);
    try {
      GroupValidation result = validator.validate("test.exists.fixture");
      assertFalse(result.valid());
      assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).contains("already")));
    } finally {
      client.deleteGroup("test.exists.fixture");
    }
  }

  @Test
  public void rejectsAncestorPrefix() {
    Group parent = new Group("test.parentcheck", "", GroupState.VERIFIED, null, 1714867200000L, 1714867200000L);
    client.insertGroup(parent);
    try {
      GroupValidation result = validator.validate("test.parentcheck.child");
      assertFalse(result.valid());
      assertTrue(result.errors().stream().anyMatch(e -> e.contains("[test.parentcheck]")));
    } finally {
      client.deleteGroup("test.parentcheck");
    }
  }

  @Test
  public void kindOfShortName() {
    assertEquals(GroupValidator.kindOf("my_handle"), GroupKind.SHORT_NAME);
  }

  @Test
  public void kindOfReverseDNS() {
    assertEquals(GroupValidator.kindOf("org.example"), GroupKind.REVERSE_DNS);
  }

  @Test
  public void kindOfGitHub() {
    assertEquals(GroupValidator.kindOf("io.github.someone"), GroupKind.REVERSE_DNS_GITHUB);
  }
}
```

- [ ] **Step 3: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: FAIL — `GroupValidator` doesn't exist.

- [ ] **Step 4: Implement `GroupValidator`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/GroupValidator.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

public class GroupValidator {
  private static final Set<String> JAVA_KEYWORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
      "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
      "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
      "interface", "long", "native", "new", "package", "private", "protected", "public",
      "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
      "throw", "throws", "transient", "try", "void", "volatile", "while",
      "true", "false", "null"
  );
  private static final Pattern LABEL_PATTERN = Pattern.compile("[a-z_$][a-z0-9_$]*");
  private final DatabaseClient databaseClient;
  private final TLDList tlds;

  public GroupValidator(DatabaseClient databaseClient, TLDList tlds) {
    this.databaseClient = databaseClient;
    this.tlds = tlds;
  }

  public static GroupKind kindOf(String name) {
    String[] labels = name.split("\\.");
    if (labels.length < 2) {
      return GroupKind.SHORT_NAME;
    }
    if (labels.length >= 3 && "io".equals(labels[0]) && "github".equals(labels[1])) {
      return GroupKind.REVERSE_DNS_GITHUB;
    }
    return GroupKind.REVERSE_DNS;
  }

  public GroupValidation validate(String name) {
    List<String> errors = new ArrayList<>();
    if (name == null || name.isBlank()) {
      errors.add("Group name is required.");
      return new GroupValidation(errors);
    }
    if (!name.chars().allMatch(c -> c < 0x80)) {
      errors.add("Group name [" + name + "] must be ASCII only.");
    }
    if (name.length() > 255) {
      errors.add("Group name length [" + name.length() + "] exceeds 255.");
    }

    String[] labels = name.split("\\.", -1);
    boolean structureValid = true;
    for (String label : labels) {
      if (label.isEmpty()) {
        errors.add("Group name [" + name + "] has an empty label (consecutive or leading/trailing dot).");
        structureValid = false;
        break;
      }
      if (!LABEL_PATTERN.matcher(label).matches()) {
        errors.add("Label [" + label + "] is not a valid Java identifier.");
        structureValid = false;
      } else if (JAVA_KEYWORDS.contains(label)) {
        errors.add("Label [" + label + "] is a Java keyword.");
        structureValid = false;
      }
    }

    if (structureValid && labels.length > 1) {
      String firstLabel = labels[0];
      if (!tlds.contains(firstLabel)) {
        errors.add("Reverse-DNS group name [" + name + "] starts with [" + firstLabel + "] which is not a TLD.");
      }
    }

    if (structureValid && databaseClient.findGroup(name).isPresent()) {
      errors.add("Group name [" + name + "] already exists.");
    }

    if (structureValid && labels.length > 1) {
      Optional<Group> ancestor = databaseClient.findAncestorGroup(name);
      if (ancestor.isPresent()) {
        errors.add("Existing group [" + ancestor.get().name() + "] is a prefix of [" + name + "].");
      }
    }

    return new GroupValidation(errors);
  }
}
```

(`Pattern` is in `java.util.regex`, covered by `import module java.base`. `Group`, `DatabaseClient`, `TLDList` are reachable via `import module org.lattejava.app`.)

- [ ] **Step 5: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: 13/13 PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/GroupValidator.java
git add src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java
git add src/test/java/module-info.java
git commit -m "feat(service): GroupValidator with TLD/identifier/ancestor checks"
```

---

## Task 5: GroupService.listForUser real implementation

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/GroupService.java`

- [ ] **Step 1: Replace the stub**

Overwrite `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/GroupService.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.service.validation.*;

public class GroupService {
  private final DatabaseClient databaseClient;
  private final GroupValidator validator;

  public GroupService(DatabaseClient databaseClient, GroupValidator validator) {
    this.databaseClient = databaseClient;
    this.validator = validator;
  }

  public List<Group> listForUser(User user) {
    return databaseClient.listGroupsForUser(user.userId());
  }
}
```

(`User` and `Group` are reachable via `import module org.lattejava.app`. `DatabaseClient` and `GroupValidator` are too — same module.)

- [ ] **Step 2: Wire dependencies in `Main.java`**

Edit `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/Main.java`. Add two new fields (alphabetized) and update the construction:

Add fields between `groupService` and `oidc`:

```java
public final GroupValidator groupValidator;
public final TLDList tldList;
```

Wait — alphabetized field names: `config, databaseClient, fusionAuth, groupService, groupValidator, oidc, oidcConfig, templates, tldList, viewService, web`. So `groupValidator` after `groupService`, and `tldList` between `templates` and `viewService`. Final order:

```java
public final Configuration config;
public final DatabaseClient databaseClient;
public final FusionAuthClient fusionAuth;
public final GroupService groupService;
public final GroupValidator groupValidator;
public final OIDC<User> oidc;
public final OIDCConfig oidcConfig;
public final JTETemplates templates;
public final TLDList tldList;
public final ViewService viewService;
public final Web web;
```

In the constructor body, change `groupService = new GroupService();` to:

```java
tldList = TLDList.fromIana();
groupValidator = new GroupValidator(databaseClient, tldList);
groupService = new GroupService(databaseClient, groupValidator);
```

Place these between `fusionAuth = ...` (which currently comes before `groupService = ...`) and `viewService = ...`. The exact location must keep dependency order intact: `databaseClient` before `groupValidator`, `groupValidator` before `groupService`, `groupService` before `viewService`.

- [ ] **Step 3: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

If you get an error about `TLDList.fromIana()` blocking on network at construction time during build/test runs that are offline, the test fixture will need a workaround — but for normal dev/test machines with internet, it works.

- [ ] **Step 4: Run MainTest**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: 4/4 PASS. The dashboard test still uses the loosened `<body` assertion (Plan 02 Task 9 restores the stronger assertion).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/GroupService.java
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(service): GroupService.listForUser hits D1; wire validator and TLD list in Main"
```

---

## Task 6: GroupService.create

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/GroupService.java`
- Create: `src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import org.lattejava.app.service.validation.*;
import org.lattejava.web.Configuration;

@Test
public class GroupServiceTest {
  public DatabaseClient client;
  public GroupService service;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    TLDList tlds = new TLDList(Set.of("org", "com", "io", "dev", "net"));
    GroupValidator validator = new GroupValidator(client, tlds);
    service = new GroupService(client, validator);
  }

  @Test
  public void createShortName_isVerifiedNoCode() {
    User creator = new User(UUID.fromString("55555555-5555-5555-5555-555555555555"), "creator@example.com", "Creator");
    try {
      Group g = service.create("test_short_fixture", "short fixture", creator);
      assertEquals(g.name(), "test_short_fixture");
      assertEquals(g.state(), GroupState.VERIFIED);
      assertNull(g.verificationCode());
      assertNotNull(g.verifiedAt());
      Optional<Member> owner = client.findMember("test_short_fixture", creator.userId());
      assertTrue(owner.isPresent());
      assertEquals(owner.get().role(), Role.OWNER);
      assertEquals(owner.get().state(), MembershipState.ACTIVE);
    } finally {
      client.deleteGroup("test_short_fixture");
    }
  }

  @Test
  public void createReverseDNS_isPendingWithCode() {
    User creator = new User(UUID.fromString("66666666-6666-6666-6666-666666666666"), "creator@example.com", "Creator");
    try {
      Group g = service.create("org.testfixture", "reverse-dns fixture", creator);
      assertEquals(g.name(), "org.testfixture");
      assertEquals(g.state(), GroupState.PENDING);
      assertNotNull(g.verificationCode());
      assertEquals(g.verificationCode().length(), 32);
      assertNull(g.verifiedAt());
      assertTrue(client.findVerification("org.testfixture").isPresent());
      Optional<Member> owner = client.findMember("org.testfixture", creator.userId());
      assertTrue(owner.isPresent());
      assertEquals(owner.get().role(), Role.OWNER);
      assertEquals(owner.get().state(), MembershipState.ACTIVE);
    } finally {
      client.deleteGroup("org.testfixture");
    }
  }

  @Test
  public void createGitHub_isPendingNoCode() {
    User creator = new User(UUID.fromString("77777777-7777-7777-7777-777777777777"), "creator@example.com", "Creator");
    try {
      Group g = service.create("io.github.testfixture", "", creator);
      assertEquals(g.state(), GroupState.PENDING);
      assertNull(g.verificationCode());
      assertFalse(client.findVerification("io.github.testfixture").isPresent());
      assertTrue(client.findMember("io.github.testfixture", creator.userId()).isPresent());
    } finally {
      client.deleteGroup("io.github.testfixture");
    }
  }

  @Test
  public void createInvalid_throws() {
    User creator = new User(UUID.fromString("88888888-8888-8888-8888-888888888888"), "creator@example.com", "Creator");
    ValidationException ex = expectThrows(
        ValidationException.class,
        () -> service.create("example.org", "forward DNS not allowed", creator)
    );
    assertFalse(ex.errors().isEmpty());
  }

  @Test
  public void listForUser_returnsCreatedGroup() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999999"), "creator@example.com", "Creator");
    try {
      service.create("test_list_for_user_fixture", "", creator);
      List<Group> groups = service.listForUser(creator);
      assertTrue(groups.stream().anyMatch(g -> g.name().equals("test_list_for_user_fixture")));
    } finally {
      client.deleteGroup("test_list_for_user_fixture");
    }
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: FAIL — `GroupService.create` doesn't exist yet.

- [ ] **Step 3: Implement `GroupService.create`**

Edit `GroupService.java`. Add a private static `SECURE_RANDOM` field, a `create` method, and a private static `generateVerificationCode` helper:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.service.validation.*;

public class GroupService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final DatabaseClient databaseClient;
  private final GroupValidator validator;

  public GroupService(DatabaseClient databaseClient, GroupValidator validator) {
    this.databaseClient = databaseClient;
    this.validator = validator;
  }

  private static String generateVerificationCode() {
    byte[] bytes = new byte[16];
    SECURE_RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public Group create(String name, String description, User creator) {
    String normalized = name == null ? null : name.toLowerCase(Locale.ROOT).trim();
    GroupValidation validation = validator.validate(normalized);
    if (!validation.valid()) {
      throw new ValidationException(validation.errors());
    }

    GroupKind kind = GroupValidator.kindOf(normalized);
    long now = System.currentTimeMillis();
    String desc = description == null ? "" : description;

    Group group = switch (kind) {
      case SHORT_NAME -> new Group(normalized, desc, GroupState.VERIFIED, null, now, now);
      case REVERSE_DNS_GITHUB -> new Group(normalized, desc, GroupState.PENDING, null, now, null);
      case REVERSE_DNS -> new Group(normalized, desc, GroupState.PENDING, generateVerificationCode(), now, null);
    };
    databaseClient.insertGroup(group);

    if (kind == GroupKind.REVERSE_DNS) {
      databaseClient.insertVerification(new GroupVerification(normalized, now, now));
    }

    Member ownership = new Member(normalized, creator.userId(), Role.OWNER, MembershipState.ACTIVE, null, null, now);
    databaseClient.insertMember(ownership);

    return group;
  }

  public List<Group> listForUser(User user) {
    return databaseClient.listGroupsForUser(user.userId());
  }
}
```

`HexFormat` is in `java.util` (covered by `import module java.base`). `SecureRandom` is in `java.security` (also `java.base`). `Locale` and `Pattern` similarly.

Method ordering inside the class (per `.claude/rules/code-conventions.md`): static fields → instance fields → constructors → static methods → instance methods. So:
1. `SECURE_RANDOM` (private static)
2. `databaseClient`, `validator` (private instance fields, alphabetical)
3. Constructor
4. `generateVerificationCode` (private static method)
5. `create`, `listForUser` (instance methods, alphabetical)

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: 5/5 PASS.

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: 21/21 PASS (4 MainTest + 11 DatabaseClientTest + 13 GroupValidatorTest — wait, that's 28; let me recount: 4 MainTest + 11 DatabaseClientTest after Task 1 + 13 GroupValidatorTest + 5 GroupServiceTest = 33. Acceptable.)

(Don't fail if the count is slightly off — what matters is zero failures.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/GroupService.java
git add src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java
git commit -m "feat(service): GroupService.create with validation and verification-code generation"
```

---

## Task 7: Routes for groups (list, new form, create)

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`

- [ ] **Step 1: Add three handlers + register routes**

Edit `Main.java`. Inside the `prefix("/app", r -> { ... })` block, after `r.get("/dashboard", this::dashboard);`, add:

```java
r.get("/groups", this::groupsList);
r.get("/groups/new", this::groupsNewForm);
r.post("/groups/new", this::groupsCreate);
```

(Routes are explicitly NOT alphabetized — registration order has semantic meaning per the code-convention rule's "Does not apply when" clause. Group them logically: dashboard first, then groups list/new/create.)

Add three private handler methods (alphabetized in the private-method block):

```java
private void groupsCreate(HTTPRequest req, HTTPResponse res) throws IOException {
  User user = oidc.user();
  String name = req.formValue("name");
  String description = req.formValue("description");
  String kind = req.formValue("kind");
  try {
    Group group = groupService.create(name, description == null ? "" : description, user);
    res.sendRedirect("/app/groups", 303);
  } catch (ValidationException e) {
    templates.html("pages/groups/new.jte", req, res,
        Map.of(
            "view", viewService.retrieve(user),
            "sidebarGroups", viewService.retrieve(user).groupsForSidebar(),
            "kind", kind == null ? "domain" : kind,
            "name", name == null ? "" : name,
            "error", String.join(" ", e.errors())
        )
    );
  }
}

private void groupsList(HTTPRequest req, HTTPResponse res) throws IOException {
  User user = oidc.user();
  View view = viewService.retrieve(user);
  templates.html("pages/groups/list.jte", req, res,
      Map.of(
          "view", view,
          "groups", view.groupsForSidebar()
      )
  );
}

private void groupsNewForm(HTTPRequest req, HTTPResponse res) throws IOException {
  User user = oidc.user();
  View view = viewService.retrieve(user);
  templates.html("pages/groups/new.jte", req, res,
      Map.of(
          "view", view,
          "sidebarGroups", view.groupsForSidebar(),
          "kind", "domain",
          "name", "",
          "error", null
      )
  );
}
```

(`req.formValue(name)` and `res.sendRedirect(url, statusCode)` are in `org.lattejava.http`/`org.lattejava.web` — verify exact method names against the existing usage. `dashboard` handler uses `templates.html(...)`; follow that pattern.)

If `req.formValue` is named differently, look at `org.lattejava.http` HTTPRequest — the actual method might be `getParameter`, `body`, or similar. Use whatever the framework provides for reading a form-urlencoded POST body; check the dashboard handler or other working code as a reference.

Place the three handlers alphabetically in the private-method block: `dashboard, groupsCreate, groupsList, groupsNewForm, slash`.

- [ ] **Step 2: Build**

Run: `latte build`
Expected: BUILD SUCCESSFUL.

If form-value reading fails to compile, look up the framework's actual API in `org.lattejava.http` or `org.lattejava.web` and adjust.

- [ ] **Step 3: Test the routes manually (optional, requires a running app)**

This task doesn't add a test (the `MainTest.dashboard` test in Task 9 covers route presence indirectly). If you want to smoke-test:

```bash
# In one terminal:
latte run

# In another terminal:
curl -i http://localhost:8080/app/groups
# (Should redirect to /login because of the OIDC gate.)
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(routes): /app/groups, /app/groups/new (form + create)"
```

---

## Task 8: UI templates — fix paths

**Files:**
- Modify: `web/layout/sidebar.jte`
- Modify: `web/pages/groups/list.jte`
- Modify: `web/pages/groups/new.jte`
- Modify: `web/components/group-list-row.jte`

Existing templates have hardcoded paths like `/groups`, `/groups/new`, `/groups/${name}` that don't match the actual routes (which live under `/app`). Update them.

- [ ] **Step 1: Update `web/layout/sidebar.jte`**

Read the file. Replace every hardcoded path that should be under `/app`:
- `href = "/"` → `href = "/app/dashboard"`
- `href = "/groups"` → `href = "/app/groups"`
- `href = "/activity"` → `href = "/app/activity"` (route doesn't exist yet; leave the link present)
- `href = "/account"` → `href = "/app/account"` (same)

Don't touch sidebar nav items that aren't under `/app/*` (e.g. logo or external links).

- [ ] **Step 2: Update `web/pages/groups/list.jte`**

In the "New group" button, change `href = "/groups/new"` to `href = "/app/groups/new"`.
In the form `<form ... action="/groups">`, change to `action="/app/groups"`.

- [ ] **Step 3: Update `web/pages/groups/new.jte`**

- `<a ... href="/groups">Groups</a>` → `href="/app/groups"`
- `<form method="post" action="/groups/new">` → `action="/app/groups/new"`
- `@template.components.button(... href = "/groups")` → `href = "/app/groups"`

- [ ] **Step 4: Update `web/components/group-list-row.jte`**

- `href="/groups/${group.name()}"` → `href="/app/groups/${group.name()}"` (the `/app/groups/{name}` route doesn't exist yet but Plan 03+ wires it; the link should already point there).

- [ ] **Step 5: Build + run MainTest**

Run: `latte build && latte test --test=org.lattejava.app.tests.MainTest`
Expected: BUILD SUCCESSFUL; 4/4 PASS.

- [ ] **Step 6: Commit**

```bash
git add web/layout/sidebar.jte
git add web/pages/groups/list.jte
git add web/pages/groups/new.jte
git add web/components/group-list-row.jte
git commit -m "fix(ui): prefix template paths with /app for groups routes"
```

---

## Task 9: Restore MainTest.dashboard assertion

**Files:**
- Modify: `src/test/java/org/lattejava/app/tests/MainTest.java`

The `MainTest.dashboard` test was loosened in Plan 01 from `s.contains("org.lattejava")` to just `s.contains("<body")` because `GroupService.listForUser` returned an empty list. Now that the seed runs (BeforeSuite) AND `GroupService.listForUser` queries D1, the sidebar should render `org.lattejava`. Restore the original assertion.

- [ ] **Step 1: Update the assertion**

In `src/test/java/org/lattejava/app/tests/MainTest.java`, locate the `dashboard` test:

```java
@Test
public void dashboard() throws Exception {
  var string = new StringBodyAsserter();
  oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  test.get("/app/dashboard")
      .assertStatus(200)
      .assertBodyAs(string, s -> s.contains("<body"));
}
```

Change the body assertion:

```java
.assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
```

- [ ] **Step 2: Run MainTest**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: 4/4 PASS — the dashboard now includes `org.lattejava` in the rendered HTML thanks to the seeded membership.

If the test fails, the most likely reason is the seeded membership row didn't make it (FA test user lookup failure or schema mismatch). Re-read `MainTest.beforeSuite()` and confirm it's running cleanly.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "test: restore dashboard assertion for org.lattejava in sidebar"
```

---

## Self-review checklist

- All five validation rules from the design doc are exercised by `GroupValidatorTest`?
- `GroupKind.SHORT_NAME` / `REVERSE_DNS` / `REVERSE_DNS_GITHUB` correctly drive different creation paths?
- Verification code is 32 lowercase hex chars (16 bytes from SecureRandom)?
- TLDList downloads from IANA at startup, fails loudly on download failure?
- `GroupService.create` inserts group, optionally verification row, and ALWAYS inserts an OWNER membership for the creator?
- `findAncestorGroup` uses dot-boundary prefix matching (does not match `test.examples` against `test.example.foo`)?
- `listGroupsForUser` joins members + groups so it returns groups for both ACTIVE and PENDING memberships?
- All public methods on `DatabaseClient` and `GroupService` alphabetized within visibility?
- Copyright headers on every new Java file?
- Error messages bracket runtime values (`[group_name]`, `[label]`, etc.)?
- All hardcoded template paths under `/app/*`?
- Dashboard test asserts `org.lattejava` is rendered?

---

## What this plan deliberately does NOT do

- **DNS verification scanner** (Plan 03 — background task hits TXT records, atomic state-update + verification-row delete).
- **GitHub OAuth verification** (Plan 03 — IDP link, `/orgs/{org}/members/{username}` lookup, scope wiring in kickstart).
- **Group detail page** (`/app/groups/{name}` route, `groups/detail.jte` rendering — Plan 03 owns the verification UI; Plan 04 owns members/settings).
- **Membership flows** (invite/accept/decline/remove/role/leave — Plan 04).
- **Group deletion** (Plan 05).
- **Kickstart updates** (Plan 06 — SMTP, GitHub IDP, email templates, removal of FA Group EntityType).
