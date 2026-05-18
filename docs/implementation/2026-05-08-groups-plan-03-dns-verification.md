# Groups Plan 03 — DNS Verification

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the DNS verification path: a background scanner that polls each pending `group_verifications` row, performs a DNS TXT lookup against the reversed group name, and atomically marks the group `VERIFIED` (deleting the verification row) on match, or `FAILED` after the 48-hour window. Wire the group detail page (`/app/groups/{name}`) so a user can see verification status, add the TXT record, trigger an immediate check, and retry a failed verification.

**Architecture:** A `DNSResolver` interface abstracts JNDI-based TXT lookups so tests can inject a deterministic fake. `VerificationService` owns the scan loop (find pending → lookup TXT → apply outcome via `DatabaseClient`) and the retry trigger. `Main` constructs the service and starts a `ScheduledExecutorService` that ticks every three minutes. The existing `VerificationChallenge` UI carrier and JTE templates (`detail.jte`, `overview.jte`, `verify.jte`) are wired against real `Group` + `GroupVerification` data.

**Tech Stack:** Java 25 (JPMS), JNDI DNS provider (`jdk.naming.dns`) for TXT lookups, `ScheduledExecutorService` for cadence, existing `DatabaseClient` over D1. Tests use real D1 + a fake `DNSResolver`.

**Reference design:** `docs/design/2026-05-07-groups.md` (Reverse-DNS group verification section). Key points:
- TXT record host: the apex of the reversed group name (e.g. `org.example` → `example.org`).
- TXT record value: the bare `verification_code` from the `groups` row.
- Scan cadence: 3 minutes.
- Failure window: 48 hours from `started_at`.
- DNS errors are silently ignored (re-tried next tick).
- "Duplicate checks and race conditions are not a concern" — sequential D1 writes (`UPDATE groups`, then `DELETE FROM group_verifications`) are acceptable; eventual consistency wins.
- Re-try button creates a new `group_verifications` row (same `verification_code`).

**Out of scope (deferred):**
- **GitHub verification** — depends on Plan 06's kickstart updates (GitHub IDP with `read:org` scope). The original phasing put DNS + GitHub in Plan 03; GitHub is split off because the kickstart isn't there yet.
- **Public group pages** — design says "Group pages are accessible to anyone." Plan 03 keeps `/app/groups/{name}` behind the OIDC gate. Public access wires up later.
- **Group deletion** (Plan 05).
- **Membership tabs** (members/settings/leave) — Plan 04. Plan 03 wires the **Overview** tab only; the **Members**/**Settings**/**Activity** tabs render their existing stub content.

---

## File Structure

**Create:**
- `src/main/java/org/lattejava/app/util/DNSResolver.java` — interface with one method `List<String> lookupTXT(String name)`.
- `src/main/java/org/lattejava/app/util/JNDIDNSResolver.java` — default impl using `javax.naming.directory.InitialDirContext` and `com.sun.jndi.dns.DnsContextFactory`.
- `src/main/java/org/lattejava/app/service/VerificationService.java` — scan + retry + challenge-build orchestration.
- `src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java` — integration tests against real D1 with a fake `DNSResolver`.

**Modify:**
- `src/main/java/org/lattejava/app/db/DatabaseClient.java` — add `listVerificationsDueForCheck`, `updateGroupState`, `updateVerificationLastChecked`.
- `src/main/java/module-info.java` — `requires java.naming;` (for JNDI). The `jdk.naming.dns` provider is loaded via `ServiceLoader`; no explicit `requires` needed for the provider itself, but verify at runtime.
- `src/main/java/org/lattejava/app/Main.java` — construct `DNSResolver`, `VerificationService`, schedule the scanner, add three routes (`GET /app/groups/{name}`, `POST /app/groups/{name}/verify/check`, `POST /app/groups/{name}/verify/retry`), shut down the scheduler in `close()`.
- `web/pages/groups/detail.jte` — fix hardcoded `/groups` paths; use real Group data; expose `Members`/`Settings`/`Activity`/`Artifacts` tabs as placeholder content.
- `web/pages/groups/overview.jte` — fix hardcoded `/groups` paths; use real Group state.
- `web/pages/groups/verify.jte` — fix hardcoded `/groups` paths; build `VerificationChallenge` from real data.

**Delete:** none.

---

## Decisions locked in for this plan

- **TXT record location:** apex of reversed group name. For `org.example` → query `example.org` IN TXT. Match if any returned TXT record's value equals the group's `verification_code` (case-sensitive — the code is hex, no case ambiguity).
- **Scanner cadence:** 3 minutes via `ScheduledExecutorService.scheduleAtFixedRate(...)`. Started in `Main.main()` after construction; shut down in `Main.close()`.
- **Failure window:** 48 hours from `started_at`. The scanner checks both: if TXT match → `VERIFIED`; if `now - started_at >= 48h` and no match → `FAILED`. Otherwise update `last_checked_at` and continue.
- **Sequential writes:** `UPDATE groups` then `DELETE FROM group_verifications`. If the DELETE fails, next scan retries. Per design: eventual consistency, last writer wins.
- **DNSResolver interface:** single method, throws no checked exceptions (impl converts JNDI exceptions to `RuntimeException` or returns empty). Tests inject a `Map<String, List<String>>`-backed fake.
- **Per-tick error isolation:** an exception in one verification check must not abort the scan loop. Wrap each `checkOne` call in try/catch; log + continue.
- **`VerificationChallenge` shape:** the existing record has `domain, recordName, recordValue, startedAt, lastCheckedAt, dnsRecordFound, valueMatches`. We populate `recordName = recordValue's host = apex of reversed name`, `recordValue = group.verificationCode()`, dates as ISO-8601 strings, and the two booleans default to `false` (Plan 03 doesn't surface mid-check progress; the booleans are leftovers — we set them to `false` always, and Plan 04 may remove them).
- **JNDI without explicit `requires jdk.naming.dns`:** the `jdk.naming.dns` module provides the `DnsContextFactory` via `ServiceLoader`. We just `requires java.naming;` and use `com.sun.jndi.dns.DnsContextFactory` by class name string — JNDI resolves it at runtime.
- **Manual-check endpoint:** `POST /app/groups/{name}/verify/check` runs `checkOne` synchronously for that group's verification. The verify.jte form already posts there; we wire the handler.
- **Retry endpoint:** `POST /app/groups/{name}/verify/retry` only acts if state is `FAILED`. Sets state back to `PENDING` and inserts a fresh `group_verifications` row with `started_at = now`. Same `verification_code` (immutable per design).
- **Scope discipline on tabs:** the group detail page renders the `overview` tab in Plan 03. The `members`, `settings`, `activity`, `artifacts` tabs route to placeholder content (existing stub templates). Plan 04 wires those.

---

## Task 1: DNSResolver interface + JNDIDNSResolver impl

**Files:**
- Create: `src/main/java/org/lattejava/app/util/DNSResolver.java`
- Create: `src/main/java/org/lattejava/app/util/JNDIDNSResolver.java`
- Modify: `src/main/java/module-info.java` — add `requires java.naming;`

- [ ] **Step 1: Create the interface**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/util/DNSResolver.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.util;

import module java.base;

/**
 * Looks up DNS TXT records for a host. Implementations must not throw checked exceptions; on
 * resolution failure they return an empty list (the caller treats failure as "not found yet").
 */
public interface DNSResolver {
  /**
   * Returns every TXT record value for {@code host}. Multi-string TXT records are concatenated
   * by the underlying JNDI provider into a single string; that's the form returned here.
   *
   * @param host The fully-qualified host to query (e.g. {@code "example.org"}).
   * @return All TXT values, or an empty list if the host has no TXT records or resolution failed.
   */
  List<String> lookupTXT(String host);
}
```

- [ ] **Step 2: Create the JNDI implementation**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/util/JNDIDNSResolver.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.util;

import module java.base;
import module java.naming;

import org.lattejava.app.service.dns.*;

public class JNDIDNSResolver implements DNSResolver {
  private static final String FACTORY = "com.sun.jndi.dns.DnsContextFactory";

  @Override
  public List<String> lookupTXT(String host) {
    Hashtable<String, String> env = new Hashtable<>();
    env.put("java.naming.factory.initial", FACTORY);
    env.put("java.naming.provider.url", "dns:");
    try {
      DirContext ctx = new InitialDirContext(env);
      try {
        Attributes attrs = ctx.getAttributes(host, new String[]{"TXT"});
        Attribute txt = attrs.get("TXT");
        if (txt == null) {
          return List.of();
        }
        List<String> result = new ArrayList<>(txt.size());
        NamingEnumeration<?> values = txt.getAll();
        while (values.hasMore()) {
          Object value = values.next();
          if (value != null) {
            result.add(value.toString());
          }
        }
        return result;
      } finally {
        ctx.close();
      }
    } catch (NamingException e) {
      return List.of();
    }
  }
}
```

(`Hashtable`, `ArrayList`, `List` — `java.base`. `DirContext`, `InitialDirContext`, `Attributes`, `Attribute`, `NamingEnumeration`, `NamingException` — `java.naming`. The `com.sun.jndi.dns.DnsContextFactory` lives in the `jdk.naming.dns` module which is loaded automatically as a JNDI service provider.)

- [ ] **Step 3: Update module-info**

Edit `/Users/bpontarelli/dev/latte-java/app/src/main/java/module-info.java`. Add `requires java.naming;` alphabetized. Final `requires` block:

```java
requires com.fasterxml.jackson.databind;
requires fusionauth.java.client;
requires gg.jte;
requires gg.jte.runtime;
requires java.naming;
requires java.net.http;
requires org.lattejava.http;
requires org.lattejava.jwt;
requires org.lattejava.web;
```

- [ ] **Step 4: Build**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: 34/34 PASS (no regression; the new resolver isn't wired into anything yet).

- [ ] **Step 6: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/app
git add src/main/java/org/lattejava/app/util/DNSResolver.java
git add src/main/java/org/lattejava/app/util/JNDIDNSResolver.java
git add src/main/java/module-info.java
git commit -m "feat(util): DNSResolver interface + JNDI-based TXT lookup impl"
```

Stage only those three files. Don't touch `wrangler.toml`, `app.iml`, or `docs/`.

---

## Task 2: DatabaseClient — verification helpers

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java`

- [ ] **Step 1: Write failing tests**

Append to `DatabaseClientTest.java`:

```java
@Test
public void listVerificationsDueForCheck_returnsRowsBelowThreshold() {
  Group g = new Group("test.due.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    client.insertVerification(new GroupVerification("test.due.fixture", 1714867200000L, 1714867200000L));
    List<GroupVerification> due = client.listVerificationsDueForCheck(1714867500000L);
    assertTrue(due.stream().anyMatch(v -> v.groupName().equals("test.due.fixture")));
  } finally {
    client.deleteGroup("test.due.fixture");
  }
}

@Test
public void listVerificationsDueForCheck_excludesRowsAboveThreshold() {
  Group g = new Group("test.notdue.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    client.insertVerification(new GroupVerification("test.notdue.fixture", 1714867200000L, 1714867500000L));
    // Threshold strictly LESS than last_checked_at means this row is not due.
    List<GroupVerification> due = client.listVerificationsDueForCheck(1714867200000L);
    assertFalse(due.stream().anyMatch(v -> v.groupName().equals("test.notdue.fixture")));
  } finally {
    client.deleteGroup("test.notdue.fixture");
  }
}

@Test
public void updateGroupState_changesStateAndVerifiedAt() {
  Group g = new Group("test.update.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    client.updateGroupState("test.update.fixture", GroupState.VERIFIED, 1714867500000L);
    Optional<Group> after = client.findGroup("test.update.fixture");
    assertTrue(after.isPresent());
    assertEquals(after.get().state(), GroupState.VERIFIED);
    assertEquals(after.get().verifiedAt(), Long.valueOf(1714867500000L));
  } finally {
    client.deleteGroup("test.update.fixture");
  }
}

@Test
public void updateVerificationLastChecked_updatesTimestamp() {
  Group g = new Group("test.lastcheck.fixture", "", GroupState.PENDING, "code", 1714867200000L, null);
  client.insertGroup(g);
  try {
    client.insertVerification(new GroupVerification("test.lastcheck.fixture", 1714867200000L, 1714867200000L));
    client.updateVerificationLastChecked("test.lastcheck.fixture", 1714867600000L);
    Optional<GroupVerification> after = client.findVerification("test.lastcheck.fixture");
    assertTrue(after.isPresent());
    assertEquals(after.get().lastCheckedAt(), Long.valueOf(1714867600000L));
  } finally {
    client.deleteGroup("test.lastcheck.fixture");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: FAIL — three new methods don't exist.

- [ ] **Step 3: Implement the three methods**

Add to `DatabaseClient.java` in alphabetical position among existing public methods. After this change the public-method order becomes:
`deleteGroup, deleteMember, deleteVerification, findAncestorGroup, findGroup, findMember, findVerification, insertGroup, insertMember, insertVerification, listGroupsForUser, listVerificationsDueForCheck, query, updateGroupState, updateVerificationLastChecked`.

```java
public List<GroupVerification> listVerificationsDueForCheck(long lastCheckedAtBefore) {
  D1Response response = query(
      "SELECT group_name, started_at, last_checked_at FROM group_verifications "
          + "WHERE last_checked_at IS NULL OR last_checked_at <= ?",
      lastCheckedAtBefore
  );
  List<Map<String, Object>> rows = response.result().getFirst().results();
  List<GroupVerification> verifications = new ArrayList<>(rows.size());
  for (Map<String, Object> row : rows) {
    verifications.add(rowToVerification(row));
  }
  return verifications;
}

public void updateGroupState(String name, GroupState state, Long verifiedAt) {
  query(
      "UPDATE groups SET state = ?, verified_at = ? WHERE name = ?",
      state.name(),
      verifiedAt,
      name
  );
}

public void updateVerificationLastChecked(String groupName, long lastCheckedAt) {
  query(
      "UPDATE group_verifications SET last_checked_at = ? WHERE group_name = ?",
      lastCheckedAt,
      groupName
  );
}
```

(`rowToVerification` — already a private static helper in `DatabaseClient`. If not present, double-check; Plan 02 added the read methods using it.)

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.db.DatabaseClientTest`
Expected: 15/15 PASS (the prior 11 + 4 new).

- [ ] **Step 5: Run MainTest**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: 4/4 PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/app
git add src/main/java/org/lattejava/app/db/DatabaseClient.java
git add src/test/java/org/lattejava/app/tests/db/DatabaseClientTest.java
git commit -m "feat(db): listVerificationsDueForCheck + updateGroupState + updateVerificationLastChecked"
```

---

## Task 3: VerificationService — checkOne (single verification outcome)

**Files:**
- Create: `src/main/java/org/lattejava/app/service/VerificationService.java`
- Create: `src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java`

This task lays down the service skeleton + the per-verification logic with an injected fake DNSResolver. Subsequent tasks add `scan`, `retry`, `buildChallenge`.

- [ ] **Step 1: Write failing tests**

Create `/Users/bpontarelli/dev/latte-java/app/src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java`:

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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
import org.lattejava.app.model.GroupVerification;
import org.lattejava.app.service.dns.*;
import org.lattejava.web.Configuration;

@Test
public class VerificationServiceTest {
  public DatabaseClient client;
  public FakeDNSResolver resolver;
  public VerificationService service;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    resolver = new FakeDNSResolver();
    service = new VerificationService(client, resolver);
  }

  @BeforeMethod
  public void resetResolver() {
    resolver.responses.clear();
  }

  @Test
  public void checkOne_matchesTxt_marksVerifiedAndDeletesRow() {
    Group g = new Group("test.checkone.match", "", GroupState.PENDING, "code-match", 1L, null);
    client.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.match", 1L, 1L);
    client.insertVerification(v);
    resolver.responses.put("match.checkone.test", List.of("code-match"));
    try {
      service.checkOne(v, 100L);

      Optional<Group> after = client.findGroup("test.checkone.match");
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Long.valueOf(100L));
      assertFalse(client.findVerification("test.checkone.match").isPresent());
    } finally {
      client.deleteGroup("test.checkone.match");
    }
  }

  @Test
  public void checkOne_noMatch_updatesLastCheckedOnly() {
    Group g = new Group("test.checkone.miss", "", GroupState.PENDING, "code-miss", 1L, null);
    client.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.miss", 1L, 1L);
    client.insertVerification(v);
    resolver.responses.put("miss.checkone.test", List.of("not-the-code"));
    try {
      service.checkOne(v, 50L);

      Optional<Group> after = client.findGroup("test.checkone.miss");
      assertEquals(after.get().state(), GroupState.PENDING);
      assertNull(after.get().verifiedAt());
      Optional<GroupVerification> verAfter = client.findVerification("test.checkone.miss");
      assertTrue(verAfter.isPresent());
      assertEquals(verAfter.get().lastCheckedAt(), Long.valueOf(50L));
    } finally {
      client.deleteGroup("test.checkone.miss");
    }
  }

  @Test
  public void checkOne_pastDeadline_marksFailedAndDeletesRow() {
    long started = 1L;
    long deadline = started + Duration.ofHours(48).toMillis();
    Group g = new Group("test.checkone.late", "", GroupState.PENDING, "code-late", started, null);
    client.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.late", started, started);
    client.insertVerification(v);
    // No DNS match — but deadline has elapsed.
    try {
      service.checkOne(v, deadline + 1);

      Optional<Group> after = client.findGroup("test.checkone.late");
      assertEquals(after.get().state(), GroupState.FAILED);
      assertNull(after.get().verifiedAt());
      assertFalse(client.findVerification("test.checkone.late").isPresent());
    } finally {
      client.deleteGroup("test.checkone.late");
    }
  }

  // Inner fake — keyed by host (forward-DNS apex), value is the list of TXT strings to return.
  public static class FakeDNSResolver implements DNSResolver {
    public final Map<String, List<String>> responses = new HashMap<>();

    @Override
    public List<String> lookupTXT(String host) {
      return responses.getOrDefault(host, List.of());
    }
  }
}
```

(The fake's host keys use the reversed group name — for `test.checkone.match`, the reverse is `match.checkone.test`. The reversal is what `VerificationService` will compute internally.)

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: FAIL — `VerificationService` doesn't exist.

- [ ] **Step 3: Implement `VerificationService` with `checkOne`**

Create `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/service/VerificationService.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.service.dns.*;

public class VerificationService {
  public static final long DEADLINE_MILLIS = Duration.ofHours(48).toMillis();
  private final DatabaseClient databaseClient;
  private final DNSResolver resolver;

  public VerificationService(DatabaseClient databaseClient, DNSResolver resolver) {
    this.databaseClient = databaseClient;
    this.resolver = resolver;
  }

  public static String forwardDomain(String reverseDNSName) {
    String[] labels = reverseDNSName.split("\\.");
    StringBuilder out = new StringBuilder();
    for (int i = labels.length - 1; i >= 0; i--) {
      if (out.length() > 0) {
        out.append('.');
      }
      out.append(labels[i]);
    }
    return out.toString();
  }

  public void checkOne(GroupVerification verification, long now) {
    Optional<Group> groupOpt = databaseClient.findGroup(verification.groupName());
    if (groupOpt.isEmpty()) {
      databaseClient.deleteVerification(verification.groupName());
      return;
    }
    Group group = groupOpt.get();
    String code = group.verificationCode();
    if (code == null) {
      databaseClient.deleteVerification(verification.groupName());
      return;
    }
    String host = forwardDomain(verification.groupName());
    List<String> txtRecords;
    try {
      txtRecords = resolver.lookupTXT(host);
    } catch (RuntimeException e) {
      txtRecords = List.of();
    }
    boolean matched = txtRecords.stream().anyMatch(code::equals);
    if (matched) {
      databaseClient.updateGroupState(verification.groupName(), GroupState.VERIFIED, now);
      databaseClient.deleteVerification(verification.groupName());
      return;
    }
    if (now - verification.startedAt() >= DEADLINE_MILLIS) {
      databaseClient.updateGroupState(verification.groupName(), GroupState.FAILED, null);
      databaseClient.deleteVerification(verification.groupName());
      return;
    }
    databaseClient.updateVerificationLastChecked(verification.groupName(), now);
  }
}
```

Member ordering: static fields → instance fields → constructor → static methods (`forwardDomain`) → instance methods (`checkOne`).

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: 3/3 PASS.

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: 38/38 PASS (zero failures; expect counts to grow as more tasks land).

- [ ] **Step 6: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/app
git add src/main/java/org/lattejava/app/service/VerificationService.java
git add src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java
git commit -m "feat(service): VerificationService.checkOne with DNS lookup + state transitions"
```

---

## Task 4: VerificationService.scan — iterate due rows

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/VerificationService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java`

- [ ] **Step 1: Write failing test**

Append to `VerificationServiceTest.java`:

```java
@Test
public void scan_processesAllDueVerifications() {
  Group g1 = new Group("test.scan.one", "", GroupState.PENDING, "code-one", 1L, null);
  Group g2 = new Group("test.scan.two", "", GroupState.PENDING, "code-two", 1L, null);
  client.insertGroup(g1);
  client.insertGroup(g2);
  client.insertVerification(new GroupVerification("test.scan.one", 1L, 1L));
  client.insertVerification(new GroupVerification("test.scan.two", 1L, 1L));
  resolver.responses.put("one.scan.test", List.of("code-one"));
  // 'two' has no matching TXT response.
  try {
    service.scan(200L);

    Optional<Group> one = client.findGroup("test.scan.one");
    assertEquals(one.get().state(), GroupState.VERIFIED);
    assertFalse(client.findVerification("test.scan.one").isPresent());

    Optional<Group> two = client.findGroup("test.scan.two");
    assertEquals(two.get().state(), GroupState.PENDING);
    Optional<GroupVerification> twoVer = client.findVerification("test.scan.two");
    assertTrue(twoVer.isPresent());
    assertEquals(twoVer.get().lastCheckedAt(), Long.valueOf(200L));
  } finally {
    client.deleteGroup("test.scan.one");
    client.deleteGroup("test.scan.two");
  }
}

@Test
public void scan_isolatesPerVerificationFailures() {
  Group g1 = new Group("test.scan.iso1", "", GroupState.PENDING, "code-iso1", 1L, null);
  Group g2 = new Group("test.scan.iso2", "", GroupState.PENDING, "code-iso2", 1L, null);
  client.insertGroup(g1);
  client.insertGroup(g2);
  client.insertVerification(new GroupVerification("test.scan.iso1", 1L, 1L));
  client.insertVerification(new GroupVerification("test.scan.iso2", 1L, 1L));
  // iso1's resolver call will throw; iso2 should still be processed.
  resolver.responses.put("iso2.scan.test", List.of("code-iso2"));
  resolver.throwingHosts.add("iso1.scan.test");
  try {
    service.scan(200L);

    Optional<Group> iso2 = client.findGroup("test.scan.iso2");
    assertEquals(iso2.get().state(), GroupState.VERIFIED);
  } finally {
    client.deleteGroup("test.scan.iso1");
    client.deleteGroup("test.scan.iso2");
  }
}
```

The second test uses a `throwingHosts` set on the fake — extend the inner class:

Modify the existing `FakeDNSResolver` static inner class to add the `throwingHosts` field and throw when matched:

```java
public static class FakeDNSResolver implements DNSResolver {
  public final Map<String, List<String>> responses = new HashMap<>();
  public final Set<String> throwingHosts = new HashSet<>();

  @Override
  public List<String> lookupTXT(String host) {
    if (throwingHosts.contains(host)) {
      throw new RuntimeException("simulated DNS failure for [" + host + "]");
    }
    return responses.getOrDefault(host, List.of());
  }
}
```

Update `resetResolver()`:

```java
@BeforeMethod
public void resetResolver() {
  resolver.responses.clear();
  resolver.throwingHosts.clear();
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: FAIL — `VerificationService.scan` doesn't exist.

- [ ] **Step 3: Implement `scan`**

Add to `VerificationService.java` in alphabetical position among instance methods (between `checkOne` and any other):

```java
public void scan(long now) {
  List<GroupVerification> due = databaseClient.listVerificationsDueForCheck(now);
  for (GroupVerification v : due) {
    try {
      checkOne(v, now);
    } catch (RuntimeException e) {
      // Per-verification failures must not abort the loop.
    }
  }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: 5/5 PASS.

- [ ] **Step 5: Run all tests**

Run: `latte test`
Expected: zero failures (counts grow).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/VerificationService.java
git add src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java
git commit -m "feat(service): VerificationService.scan iterates due verifications"
```

---

## Task 5: VerificationService.retry

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/VerificationService.java`
- Modify: `src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java`

- [ ] **Step 1: Write failing tests**

Append to `VerificationServiceTest.java`:

```java
@Test
public void retry_failedGroup_movesBackToPendingAndInsertsVerification() {
  Group g = new Group("test.retry.failed", "", GroupState.FAILED, "code-retry", 1L, null);
  client.insertGroup(g);
  try {
    service.retry("test.retry.failed", 500L);
    Optional<Group> after = client.findGroup("test.retry.failed");
    assertEquals(after.get().state(), GroupState.PENDING);
    assertNull(after.get().verifiedAt());
    Optional<GroupVerification> ver = client.findVerification("test.retry.failed");
    assertTrue(ver.isPresent());
    assertEquals(ver.get().startedAt(), 500L);
  } finally {
    client.deleteGroup("test.retry.failed");
  }
}

@Test
public void retry_pendingGroup_isNoOp() {
  Group g = new Group("test.retry.pending", "", GroupState.PENDING, "code-noop", 1L, null);
  client.insertGroup(g);
  client.insertVerification(new GroupVerification("test.retry.pending", 1L, 1L));
  try {
    service.retry("test.retry.pending", 500L);
    Optional<Group> after = client.findGroup("test.retry.pending");
    assertEquals(after.get().state(), GroupState.PENDING);
    Optional<GroupVerification> ver = client.findVerification("test.retry.pending");
    assertTrue(ver.isPresent());
    assertEquals(ver.get().startedAt(), 1L); // unchanged
  } finally {
    client.deleteGroup("test.retry.pending");
  }
}

@Test
public void retry_verifiedGroup_isNoOp() {
  Group g = new Group("test.retry.verified", "", GroupState.VERIFIED, null, 1L, 1L);
  client.insertGroup(g);
  try {
    service.retry("test.retry.verified", 500L);
    Optional<Group> after = client.findGroup("test.retry.verified");
    assertEquals(after.get().state(), GroupState.VERIFIED);
    assertFalse(client.findVerification("test.retry.verified").isPresent());
  } finally {
    client.deleteGroup("test.retry.verified");
  }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: FAIL — `retry` doesn't exist.

- [ ] **Step 3: Implement `retry`**

Add to `VerificationService.java` (alphabetical position among instance methods — between `checkOne` and `scan`):

```java
public void retry(String groupName, long now) {
  Optional<Group> groupOpt = databaseClient.findGroup(groupName);
  if (groupOpt.isEmpty() || groupOpt.get().state() != GroupState.FAILED) {
    return;
  }
  databaseClient.updateGroupState(groupName, GroupState.PENDING, null);
  databaseClient.insertVerification(new GroupVerification(groupName, now, now));
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `latte test --test=org.lattejava.app.tests.service.VerificationServiceTest`
Expected: 8/8 PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/VerificationService.java
git add src/test/java/org/lattejava/app/tests/service/VerificationServiceTest.java
git commit -m "feat(service): VerificationService.retry resets FAILED -> PENDING + new verification row"
```

---

## Task 6: Wire VerificationService + scheduler in Main

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`

- [ ] **Step 1: Add fields, construct, schedule, shut down**

Edit `/Users/bpontarelli/dev/latte-java/app/src/main/java/org/lattejava/app/Main.java`. Add fields alphabetically by field name:

```java
public final DNSResolver dnsResolver;
public final ScheduledExecutorService scheduler;
public final VerificationService verificationService;
```

Final field block (alphabetical by field name) — insert `dnsResolver` between `databaseClient` and `fusionAuth`, `scheduler` between `oidcConfig` and `templates`, `verificationService` between `tldList` and `viewService`. Result:

```java
public final Configuration config;
public final DatabaseClient databaseClient;
public final DNSResolver dnsResolver;
public final FusionAuthClient fusionAuth;
public final GroupService groupService;
public final GroupValidator groupValidator;
public final OIDC<User> oidc;
public final OIDCConfig oidcConfig;
public final ScheduledExecutorService scheduler;
public final JTETemplates templates;
public final TLDList tldList;
public final VerificationService verificationService;
public final ViewService viewService;
public final Web web;
```

In the constructor body, after `groupService = new GroupService(databaseClient, groupValidator);`, add:

```java
dnsResolver = new JNDIDNSResolver();
verificationService = new VerificationService(databaseClient, dnsResolver);
scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
  Thread t = new Thread(r, "verification-scanner");
  t.setDaemon(true);
  return t;
});
```

In `main()` (the route-registration method), after `.start(PORT);` ends and BEFORE the method's closing brace, schedule the scan:

Wait — `web.install(...).start(PORT);` is the last statement of `main()`. We can't add code after a chained call. Restructure:

```java
public void main() {
  web.install(SecurityHeaders.builder()...)
     .install(oidc)
     .baseDir(BASE_DIR)
     .files("/static")
     .get("/", this::slash)
     .prefix("/app", r -> {
       r.install(oidc.authenticated());
       r.get("/dashboard", this::dashboard);
       r.get("/groups", this::groupsList);
       r.get("/groups/new", this::groupsNewForm);
       r.post("/groups/new", this::groupsCreate);
     })
     .start(PORT);

  scheduler.scheduleAtFixedRate(
      () -> verificationService.scan(System.currentTimeMillis()),
      Duration.ofMinutes(3).toMillis(),
      Duration.ofMinutes(3).toMillis(),
      TimeUnit.MILLISECONDS
  );
}
```

In `close()`, shut down the scheduler:

```java
public void close() {
  scheduler.shutdownNow();
  web.close();
}
```

`Executors`, `ScheduledExecutorService`, `TimeUnit`, `Duration`, `Thread` are in `java.base` (covered by `import module java.base`).

- [ ] **Step 2: Build**

Run: `cd /Users/bpontarelli/dev/latte-java/app && latte build`
Expected: BUILD SUCCESSFUL.

If a name resolution issue arises (e.g. `DNSResolver` ambiguous), add explicit class imports:

```java


```

- [ ] **Step 3: Run all tests**

Run: `latte test`
Expected: zero failures. The `MainTest.beforeSuite` constructs `Main`, which now starts the scheduler — but the scheduler doesn't fire its first task for 3 minutes, so it has no observable effect on tests. `MainTest.afterSuite` calls `main.close()` which shuts the scheduler down.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(verification): construct VerificationService and start 3-minute scanner in Main"
```

---

## Task 7: Routes — group detail, manual check, retry

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`

The detail page currently isn't routed. Plan 03 wires three routes (and the framework's path-parameter syntax — discover via the framework before coding):
- `GET /app/groups/{name}` — group detail (Overview tab).
- `POST /app/groups/{name}/verify/check` — synchronous check; same `checkOne` the scanner uses.
- `POST /app/groups/{name}/verify/retry` — only acts if state is `FAILED`.

- [ ] **Step 1: Discover the path-parameter API**

Look at `org.lattejava.http`/`org.lattejava.web` for how to declare a path parameter and read its value. Likely candidates:
- `r.get("/groups/{name}", handler)` and `req.getPathParameter("name")` or `req.pathParameter("name")`.
- `r.get("/groups/:name", handler)` and `req.getParam("name")`.

Run `find / -path '*lattejava*' -name '*.jar' 2>/dev/null | grep -E '(http|web)' | head -3` to locate the JAR, then inspect class names with `unzip -p <jar> '<HTTPRequest>.class' 2>/dev/null | strings | grep -iE 'param|path' | head`. Or look at any other route registration in the codebase that uses path params.

If the framework uses a different shape (e.g. regex, glob), adapt accordingly.

- [ ] **Step 2: Add three handlers**

Add to `Main.java` in alphabetical position among existing private methods. After this change the order becomes: `dashboard, groupsCreate, groupsDetail, groupsList, groupsNewForm, groupsVerifyCheck, groupsVerifyRetry, slash`.

```java
private void groupsDetail(HTTPRequest req, HTTPResponse res) throws IOException {
  String groupName = req.getPathParameter("name");   // adjust per discovered API
  User user = oidc.user();
  Optional<Group> groupOpt = databaseClient.findGroup(groupName);
  if (groupOpt.isEmpty()) {
    res.setStatus(404);
    return;
  }
  Group group = groupOpt.get();
  View view = viewService.retrieve(user);
  Map<String, Object> params = new HashMap<>();
  params.put("view", view);
  params.put("group", group);
  params.put("sidebarGroups", view.groupsForSidebar());
  params.put("activeTab", "overview");
  templates.html("pages/groups/detail.jte", req, res, params);
}

private void groupsVerifyCheck(HTTPRequest req, HTTPResponse res) throws IOException {
  String groupName = req.getPathParameter("name");
  Optional<GroupVerification> verOpt = databaseClient.findVerification(groupName);
  if (verOpt.isPresent()) {
    verificationService.checkOne(verOpt.get(), System.currentTimeMillis());
  }
  res.sendRedirect("/app/groups/" + groupName, 303);
}

private void groupsVerifyRetry(HTTPRequest req, HTTPResponse res) throws IOException {
  String groupName = req.getPathParameter("name");
  verificationService.retry(groupName, System.currentTimeMillis());
  res.sendRedirect("/app/groups/" + groupName, 303);
}
```

(The `templates.html` call needs a `Map<String, Object>`; some existing handlers use `Map.of(...)` which caps at 10 entries and disallows nulls. Use `new HashMap<>()` and `params.put(...)` for safety. `templates.html(..., Map<String, Object>)` is the existing API.)

If `req.getPathParameter` is wrong, substitute the right method name. If `res.setStatus(404)` is wrong, look at how other handlers signal not-found.

- [ ] **Step 3: Register the routes**

Inside the `prefix("/app", r -> { ... })` block, after the existing groups routes:

```java
r.get("/groups/{name}", this::groupsDetail);
r.post("/groups/{name}/verify/check", this::groupsVerifyCheck);
r.post("/groups/{name}/verify/retry", this::groupsVerifyRetry);
```

(Adjust the syntax `{name}` if the framework uses a different convention.)

Routes are registered in semantic order. The two POST routes go last. The full `/app` block becomes:

```java
.prefix("/app", r -> {
  r.install(oidc.authenticated());
  r.get("/dashboard", this::dashboard);
  r.get("/groups", this::groupsList);
  r.get("/groups/new", this::groupsNewForm);
  r.get("/groups/{name}", this::groupsDetail);
  r.post("/groups/new", this::groupsCreate);
  r.post("/groups/{name}/verify/check", this::groupsVerifyCheck);
  r.post("/groups/{name}/verify/retry", this::groupsVerifyRetry);
})
```

- [ ] **Step 4: Build + tests**

Run:
```bash
latte build 2>&1 | tail -3
latte test 2>&1 | tail -8
```
Expected: BUILD SUCCESSFUL; zero failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java
git commit -m "feat(routes): /app/groups/{name} detail + verify check/retry"
```

---

## Task 8: UI wiring — detail.jte, overview.jte, verify.jte

**Files:**
- Modify: `web/pages/groups/detail.jte`
- Modify: `web/pages/groups/overview.jte`
- Modify: `web/pages/groups/verify.jte`

Two patterns to fix throughout these files:
1. Hardcoded `/groups/...` → `/app/groups/...`.
2. Build a `VerificationChallenge` from the real `Group` data (and an optional `GroupVerification` row) at render time, passed in by the handler. Update `verify.jte`'s `@param VerificationChallenge challenge` consumer to expect the new shape.

Plan 03 builds the challenge inline in the route handler when needed. To avoid handler bloat, add a static helper to `VerificationService`:

```java
public static VerificationChallenge buildChallenge(Group group, Optional<GroupVerification> verification) {
  String forward = forwardDomain(group.name());
  String startedAt = verification.map(v -> Instant.ofEpochMilli(v.startedAt()).toString()).orElse("");
  String lastCheckedAt = verification
      .map(v -> v.lastCheckedAt() == null ? "" : Instant.ofEpochMilli(v.lastCheckedAt()).toString())
      .orElse("");
  return new VerificationChallenge(
      forward,
      forward,
      group.verificationCode() == null ? "" : group.verificationCode(),
      startedAt,
      lastCheckedAt,
      false,
      false
  );
}
```

This task adds that helper and updates the three templates.

- [ ] **Step 1: Add `buildChallenge` to `VerificationService`**

Add to `VerificationService.java` in the static-methods block (alphabetically after `forwardDomain` — `buildChallenge` actually starts with `b` so it goes BEFORE `forwardDomain`):

```java
public static VerificationChallenge buildChallenge(Group group, Optional<GroupVerification> verification) {
  String forward = forwardDomain(group.name());
  String startedAt = verification.map(v -> Instant.ofEpochMilli(v.startedAt()).toString()).orElse("");
  String lastCheckedAt = verification
      .map(v -> v.lastCheckedAt() == null ? "" : Instant.ofEpochMilli(v.lastCheckedAt()).toString())
      .orElse("");
  return new VerificationChallenge(
      forward,
      forward,
      group.verificationCode() == null ? "" : group.verificationCode(),
      startedAt,
      lastCheckedAt,
      false,
      false
  );
}
```

`Instant` is in `java.time` (covered by `import module java.base`).

Also add a route handler `groupsVerifyForm` that GETs `/app/groups/{name}/verify`:

```java
private void groupsVerifyForm(HTTPRequest req, HTTPResponse res) throws IOException {
  String groupName = req.getPathParameter("name");
  User user = oidc.user();
  Optional<Group> groupOpt = databaseClient.findGroup(groupName);
  if (groupOpt.isEmpty()) {
    res.setStatus(404);
    return;
  }
  Group group = groupOpt.get();
  Optional<GroupVerification> verOpt = databaseClient.findVerification(groupName);
  VerificationChallenge challenge = VerificationService.buildChallenge(group, verOpt);
  View view = viewService.retrieve(user);
  Map<String, Object> params = new HashMap<>();
  params.put("view", view);
  params.put("group", group);
  params.put("sidebarGroups", view.groupsForSidebar());
  params.put("challenge", challenge);
  templates.html("pages/groups/verify.jte", req, res, params);
}
```

Register it in the `/app` block:
```java
r.get("/groups/{name}/verify", this::groupsVerifyForm);
```

(Place between the detail GET and the POST routes — semantic order.)

Place the new private handler alphabetically among the others.

- [ ] **Step 2: Update `web/pages/groups/detail.jte`**

Change `!{String base = "/groups/" + group.name();}` to `!{String base = "/app/groups/" + group.name();}`.

Change `<a ... href="/groups">Groups</a>` to `href="/app/groups"`.

Wherever the file references `href = base + "/verify"`, no change (already uses `base`).

- [ ] **Step 3: Update `web/pages/groups/overview.jte`**

Change `href = "/groups/" + group.name() + "/verify"` (two occurrences) to `href = "/app/groups/" + group.name() + "/verify"`.

- [ ] **Step 4: Update `web/pages/groups/verify.jte`**

The form action currently reads `action="/groups/${group.name()}/verify/check"`. Change to `action="/app/groups/${group.name()}/verify/check"`.

The breadcrumb has `<a href="/groups">Groups</a>` — change to `/app/groups`. And `href="/groups/${group.name()}"` — change to `/app/groups/${group.name()}`.

Read the rest of the file. Plan 03's `VerificationChallenge` has `recordName = forward-DNS apex`, `recordValue = bare verification_code`. The template renders `challenge.recordName()` and `challenge.recordValue()` directly — no change needed.

If the template has copy text mentioning `_latte-verify` or any prefix-style record name, update it to reflect the apex/bare-code convention. Read the file's full content; replace any specific phrasing that's now inaccurate.

- [ ] **Step 5: Build + tests**

Run:
```bash
latte build 2>&1 | tail -3
latte test 2>&1 | tail -8
```
Expected: BUILD SUCCESSFUL; zero failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/service/VerificationService.java
git add src/main/java/org/lattejava/app/Main.java
git add web/pages/groups/detail.jte
git add web/pages/groups/overview.jte
git add web/pages/groups/verify.jte
git commit -m "feat(ui): wire group detail + verify pages with real challenge data"
```

---

## Task 9: MainTest end-to-end smoke for the detail page

**Files:**
- Modify: `src/test/java/org/lattejava/app/tests/MainTest.java`

Add a smoke test that hits `/app/groups/org.lattejava` (the seeded reserved group) and asserts the rendered HTML contains the group name and the `Overview` content.

- [ ] **Step 1: Append the test**

```java
@Test
public void groupDetail() throws Exception {
  var string = new StringBodyAsserter();
  oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  test.get("/app/groups/org.lattejava")
      .assertStatus(200)
      .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
}
```

- [ ] **Step 2: Run MainTest**

Run: `latte test --test=org.lattejava.app.tests.MainTest`
Expected: 5/5 PASS.

- [ ] **Step 3: Run all tests**

Run: `latte test`
Expected: zero failures (final task).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/lattejava/app/tests/MainTest.java
git commit -m "test: end-to-end smoke for group detail page"
```

---

## Self-review checklist

- DNS lookups happen against the apex of the reversed group name?
- TXT match compares against the bare `verification_code`?
- Scanner ticks every 3 minutes via `ScheduledExecutorService`?
- Per-tick errors don't abort the loop?
- 48-hour deadline computed from `started_at`?
- Successful verification: `UPDATE groups SET state='VERIFIED', verified_at=now`, then `DELETE FROM group_verifications`?
- Failed verification (deadline exceeded with no match): `UPDATE groups SET state='FAILED'`, then `DELETE`?
- `retry` only acts if the group is in `FAILED` state?
- All new public methods on `DatabaseClient` and `VerificationService` alphabetized within visibility?
- Copyright headers on new Java files?
- Error messages bracket runtime values?
- All hardcoded template paths under `/app/*`?
- Dashboard test still asserts `org.lattejava` (Plan 02 restored that)?
- New `MainTest.groupDetail` test passes?

---

## What this plan deliberately does NOT do

- **GitHub verification** — Plan 03b (or merged with Plan 06's kickstart updates).
- **Public group pages** — `/app/groups/{name}` stays behind OIDC; public access is a future plan.
- **Members / Settings / Activity tabs** — Plan 04.
- **Group deletion + R2 check** — Plan 05.
- **Kickstart updates** — Plan 06.
