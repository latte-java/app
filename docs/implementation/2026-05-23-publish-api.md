# Publish API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/v1/publish/{groupName}`, a token-authenticated JSON endpoint that returns a presigned R2 `PUT` URL after authorizing the caller against the group that owns the artifact namespace.

**Architecture:** `web`'s `apiAuthenticated()` middleware authenticates/refreshes the bearer token and binds the JWT; an app-supplied `PublishAuthorizer` (an `APIAuthorizer`) resolves the most-specific owning group and checks VERIFIED state + ACTIVE OWNER/CONTRIBUTOR membership; `PublishController` parses the body, validates the key, and asks `PublishService` to mint a query-string SigV4 presigned URL via `R2Signer`/`R2HttpClient`.

**Tech Stack:** Java 25 (JPMS), `latte` build, JTE (unused here), Jackson, Cloudflare D1 + R2 over REST, FusionAuth OIDC via the sibling `web` module, TestNG.

**Spec:** `docs/design/2026-05-22-publish-api-design.md`

---

## Prerequisites (for every test step)

Tests boot a real server and hit real backends. Before running any `latte test`:
- FusionAuth must be running on `:9011` with the kickstart applied.
- Your D1 must be reachable (the suite wipes + reseeds rows in `@BeforeSuite`).
- No dev server may be holding port 8081 (the test server's port).

Single-class run form: `latte test --test=<fully.qualified.ClassName>`.

## File structure

**Production (create):**
- `src/main/java/org/lattejava/app/model/PublishRequest.java` — inbound JSON carrier (`fileName`).
- `src/main/java/org/lattejava/app/model/PublishResponse.java` — outbound JSON carrier (`url`).
- `src/main/java/org/lattejava/app/service/validation/PublishValidator.java` — body/key validation.
- `src/main/java/org/lattejava/app/service/PublishService.java` — validate + presign.
- `src/main/java/org/lattejava/app/security/PublishAuthorizer.java` — `APIAuthorizer` decision.
- `src/main/java/org/lattejava/app/controller/PublishController.java` — JSON in/out handler.

**Production (modify):**
- `src/main/java/org/lattejava/app/service/validation/GroupValidator.java` — reject bare-TLD short names.
- `src/main/java/org/lattejava/app/db/DatabaseClient.java` — add `findOwningGroup`.
- `src/main/java/org/lattejava/app/service/GroupService.java` — add `findOwningGroup`.
- `src/main/java/org/lattejava/app/r2/R2Client.java` — add `presignPut` to the interface.
- `src/main/java/org/lattejava/app/r2/R2HttpClient.java` — implement `presignPut`.
- `src/main/java/org/lattejava/app/r2/R2Signer.java` — add `presignedURL`.
- `src/main/java/org/lattejava/app/service/Services.java` — register `PublishService`.
- `src/main/java/org/lattejava/app/Main.java` — set introspection endpoint + register the route.

**Tests (create/modify):**
- modify `src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java`
- modify `src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java`
- create `src/test/java/org/lattejava/app/tests/r2/R2SignerTest.java`
- create `src/test/java/org/lattejava/app/tests/service/PublishValidatorTest.java`
- create `src/test/java/org/lattejava/app/tests/PublishControllerTest.java`

No `module-info.java` changes are needed: the production packages touched are already exported, `controller` is used only in-module by `Main`, and `requires` already covers Jackson, JWT, http, and web. The test packages used (`tests`, `tests.r2`, `tests.service`) are already `opens`-ed to TestNG.

---

### Task 1: Reject bare-TLD short names in `GroupValidator`

**Files:**
- Modify: `src/main/java/org/lattejava/app/service/validation/GroupValidator.java`
- Test: `src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java`

- [ ] **Step 1: Write the failing tests**

Add these two methods to `GroupValidatorTest` (its `validator` is built with `TLDList(Set.of("org","com","io","dev","net"))`):

```java
  @Test
  public void rejectsBareTld() {
    Errors errors = validator.validate(group("com"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[tld]name"));
  }

  @Test
  public void acceptsNonTldShortName() {
    assertTrue(validator.validate(group("notatld")).empty());
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: FAIL — `rejectsBareTld` fails because `validate("com")` currently returns no errors (no `[tld]name`).

- [ ] **Step 3: Implement the check**

In `GroupValidator.validate`, immediately after the existing reverse-DNS TLD block (the `if (structureValid && segments.length > 1) { ... [unknownTld]name ... }` block) and before the duplicate check, insert:

```java
    if (structureValid && segments.length == 1 && tlds.contains(name)) {
      errors.addFieldError("name", "[tld]name",
          "The group name [%s] is a top-level domain (TLD). Short group names cannot be bare TLDs; use a reverse-DNS name such as [%s.yourorg] instead.", name, name);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `latte test --test=org.lattejava.app.tests.service.GroupValidatorTest`
Expected: PASS (all methods, including the existing ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/validation/GroupValidator.java src/test/java/org/lattejava/app/tests/service/GroupValidatorTest.java
git commit -m "Reject bare-TLD short names in GroupValidator"
```

---

### Task 2: `findOwningGroup` on `DatabaseClient` and `GroupService`

Resolves the most-specific registered group covering a namespace. `DatabaseClient` runs the longest-name query; `GroupService` builds the candidate list (exact + ancestors, excluding the bare single-segment TLD).

**Files:**
- Modify: `src/main/java/org/lattejava/app/db/DatabaseClient.java`
- Modify: `src/main/java/org/lattejava/app/service/GroupService.java`
- Test: `src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add to `GroupServiceTest` (it already has `service` and `client`):

```java
  @Test
  public void findOwningGroup_picksMostSpecificRegisteredAncestor() {
    Group parent = new Group("com.owntest", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    Group child = new Group("com.owntest.child", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(parent);
    client.insertGroup(child);
    try {
      // Exact match.
      assertEquals(service.findOwningGroup("com.owntest").map(Group::name).orElse(null), "com.owntest");
      // Nested namespace under the more-specific child resolves to the child, not the parent.
      assertEquals(service.findOwningGroup("com.owntest.child.artifact").map(Group::name).orElse(null), "com.owntest.child");
      // Namespace under the parent (no more-specific group) resolves to the parent.
      assertEquals(service.findOwningGroup("com.owntest.other.thing").map(Group::name).orElse(null), "com.owntest");
      // No registered owner (the bare TLD "com" is never a candidate).
      assertTrue(service.findOwningGroup("net.unregistered.thing").isEmpty());
    } finally {
      client.deleteGroup("com.owntest.child");
      client.deleteGroup("com.owntest");
    }
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: FAIL — compile error: `findOwningGroup` does not exist on `GroupService`.

- [ ] **Step 3: Implement `DatabaseClient.findOwningGroup`**

Add this method to `DatabaseClient`, placed alphabetically between `findMember` and `findVerification`:

```java
  /**
   * Returns the most specific (longest-named) registered group among {@code candidates}, or empty if none of them is
   * registered. Used to find the group that owns an artifact namespace.
   *
   * @param candidates The candidate group names (the namespace itself plus its ancestors).
   * @return The owning group, or empty.
   */
  public Optional<Group> findOwningGroup(List<String> candidates) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    String placeholders = String.join(",", Collections.nCopies(candidates.size(), "?"));
    D1Response response = query(
        "SELECT name, description, state, verification_code, created_at, verified_at FROM groups WHERE name IN (" + placeholders + ") ORDER BY LENGTH(name) DESC LIMIT 1",
        candidates.toArray()
    );
    List<Map<String, Object>> rows = response.result().getFirst().results();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rowToGroup(rows.getFirst()));
  }
```

- [ ] **Step 4: Implement `GroupService.findOwningGroup`**

Add this method to `GroupService`, placed alphabetically between `findGroup` and `listForUser`:

```java
  /**
   * Resolves the group that owns {@code namespace} — the most specific registered group that is the namespace itself
   * or an ancestor of it. The bare single-segment prefix (a TLD such as {@code com}) is never a candidate because
   * groups cannot be bare TLDs; a no-dot short name therefore has no ancestors.
   *
   * @param namespace The artifact namespace in dotted form (e.g. {@code com.example.foo}).
   * @return The owning group, or empty if no registered group covers the namespace.
   */
  public Optional<Group> findOwningGroup(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return Optional.empty();
    }
    String normalized = namespace.trim().toLowerCase(Locale.ROOT);
    String[] segments = normalized.split("\\.");
    List<String> candidates = new ArrayList<>();
    if (segments.length == 1) {
      candidates.add(normalized);
    } else {
      for (int i = segments.length; i >= 2; i--) {
        candidates.add(String.join(".", Arrays.copyOfRange(segments, 0, i)));
      }
    }
    return databaseClient.findOwningGroup(candidates);
  }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `latte test --test=org.lattejava.app.tests.service.GroupServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/db/DatabaseClient.java src/main/java/org/lattejava/app/service/GroupService.java src/test/java/org/lattejava/app/tests/service/GroupServiceTest.java
git commit -m "Add findOwningGroup resolution to DatabaseClient and GroupService"
```

---

### Task 3: Query-string presigned PUT URL (`R2Signer` + `R2Client`/`R2HttpClient`)

**Files:**
- Modify: `src/main/java/org/lattejava/app/r2/R2Signer.java`
- Modify: `src/main/java/org/lattejava/app/r2/R2Client.java`
- Modify: `src/main/java/org/lattejava/app/r2/R2HttpClient.java`
- Test: `src/test/java/org/lattejava/app/tests/r2/R2SignerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/lattejava/app/tests/r2/R2SignerTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.r2;

import module java.base;
import module org.lattejava.app;

import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class R2SignerTest {
  @Test
  public void presignedURL_structureAndDeterminism() {
    Instant fixed = Instant.parse("2026-05-23T12:00:00Z");
    String url1 = R2Signer.presignedURL("PUT", "https", "acct.r2.cloudflarestorage.com",
        "/bucket/org/lattejava/x.jar", "AKID", "SECRET", Duration.ofMinutes(15), fixed);
    String url2 = R2Signer.presignedURL("PUT", "https", "acct.r2.cloudflarestorage.com",
        "/bucket/org/lattejava/x.jar", "AKID", "SECRET", Duration.ofMinutes(15), fixed);

    assertEquals(url1, url2);
    assertTrue(url1.startsWith("https://acct.r2.cloudflarestorage.com/bucket/org/lattejava/x.jar?"));
    assertTrue(url1.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
    assertTrue(url1.contains("X-Amz-Credential=AKID%2F20260523%2Fauto%2Fs3%2Faws4_request"));
    assertTrue(url1.contains("X-Amz-Date=20260523T120000Z"));
    assertTrue(url1.contains("X-Amz-Expires=900"));
    assertTrue(url1.contains("X-Amz-SignedHeaders=host"));
    assertTrue(url1.matches(".*X-Amz-Signature=[0-9a-f]{64}$"));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.r2.R2SignerTest`
Expected: FAIL — compile error: `R2Signer.presignedURL` does not exist.

- [ ] **Step 3: Add `R2Signer.presignedURL`**

In `R2Signer`, add this static method between `formatAmzDate` and `uriEncode` (it reuses the existing private `hmac` and `sha256Hex`):

```java
  /**
   * Builds an AWS SigV4 query-string presigned URL for a single request (no body signing — payload hash is
   * {@code UNSIGNED-PAYLOAD}, signed headers are {@code host} only). The returned URL can be handed to a client to
   * issue {@code method} directly against the object store.
   *
   * @param method          The HTTP method (e.g. {@code PUT}).
   * @param scheme          The URL scheme ({@code https} for R2).
   * @param host            The request host.
   * @param path            The already-URI-encoded absolute path (e.g. {@code /bucket/org/lattejava/x.jar}).
   * @param accessKeyId     The access key id.
   * @param secretAccessKey The secret access key.
   * @param expiry          How long the URL is valid.
   * @param now             The signing instant.
   * @return The presigned URL.
   */
  public static String presignedURL(String method, String scheme, String host, String path, String accessKeyId,
                                    String secretAccessKey, Duration expiry, Instant now) {
    String amzDate = AMZ_DATE.format(now);
    String shortDate = SHORT_DATE.format(now);
    String credentialScope = shortDate + "/" + REGION + "/" + SERVICE + "/aws4_request";

    SortedMap<String, String> query = new TreeMap<>();
    query.put("X-Amz-Algorithm", ALGORITHM);
    query.put("X-Amz-Credential", accessKeyId + "/" + credentialScope);
    query.put("X-Amz-Date", amzDate);
    query.put("X-Amz-Expires", Long.toString(expiry.toSeconds()));
    query.put("X-Amz-SignedHeaders", "host");

    String canonicalQuery = query.entrySet()
                                 .stream()
                                 .map(e -> uriEncode(e.getKey(), false) + "=" + uriEncode(e.getValue(), false))
                                 .collect(Collectors.joining("&"));
    String canonicalHeaders = "host:" + host + "\n";
    String signedHeaders = "host";

    String canonicalRequest = method + "\n" + path + "\n" + canonicalQuery + "\n"
        + canonicalHeaders + "\n" + signedHeaders + "\nUNSIGNED-PAYLOAD";

    String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

    byte[] signingKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), shortDate);
    signingKey = hmac(signingKey, REGION);
    signingKey = hmac(signingKey, SERVICE);
    signingKey = hmac(signingKey, "aws4_request");

    String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));

    return scheme + "://" + host + path + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
  }
```

- [ ] **Step 4: Add `presignPut` to the `R2Client` interface**

In `R2Client.java`, add the `java.base` module import (for `Duration`) under the package declaration, then add the method to the interface:

```java
package org.lattejava.app.r2;

import module java.base;
```

```java
  /**
   * Returns a short-lived presigned URL the caller can use to {@code PUT} an object at {@code key} into the configured
   * bucket.
   *
   * @param key    The object key (the full path within the bucket).
   * @param expiry How long the URL is valid.
   * @return The presigned PUT URL.
   */
  String presignPut(String key, Duration expiry);
```

- [ ] **Step 5: Implement `R2HttpClient.presignPut`**

In `R2HttpClient`, add this method (it reuses the existing `host`, `bucket`, `accessKeyId`, `secretAccessKey` fields):

```java
  @Override
  public String presignPut(String key, Duration expiry) {
    String path = "/" + bucket + "/" + R2Signer.uriEncode(key, true);
    return R2Signer.presignedURL("PUT", "https", host, path, accessKeyId, secretAccessKey, expiry, Instant.now());
  }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `latte test --test=org.lattejava.app.tests.r2.R2SignerTest`
Expected: PASS.

> Real-S3-backend verification of these URLs (MinIO) is deferred per the spec; this unit test covers structure and signing determinism only.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/lattejava/app/r2/R2Signer.java src/main/java/org/lattejava/app/r2/R2Client.java src/main/java/org/lattejava/app/r2/R2HttpClient.java src/test/java/org/lattejava/app/tests/r2/R2SignerTest.java
git commit -m "Add query-string presigned PUT URL generation to R2"
```

---

### Task 4: `PublishRequest` and `PublishResponse` models

**Files:**
- Create: `src/main/java/org/lattejava/app/model/PublishRequest.java`
- Create: `src/main/java/org/lattejava/app/model/PublishResponse.java`

No dedicated test — these are plain carriers exercised by Tasks 5 and 9.

- [ ] **Step 1: Create `PublishRequest`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

/**
 * Inbound JSON body of {@code POST /api/v1/publish/{groupName}}. The group name is carried in the URL path, not here.
 *
 * @param fileName The complete R2 object key the client intends to upload to.
 */
public record PublishRequest(String fileName) {
}
```

- [ ] **Step 2: Create `PublishResponse`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

/**
 * Successful JSON response of the publish endpoint.
 *
 * @param url The presigned PUT URL the client uploads the artifact to.
 */
public record PublishResponse(String url) {
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/model/PublishRequest.java src/main/java/org/lattejava/app/model/PublishResponse.java
git commit -m "Add PublishRequest and PublishResponse models"
```

---

### Task 5: `PublishValidator`

Validates the body and that the requested key is a clean key within the group's namespace prefix.

**Files:**
- Create: `src/main/java/org/lattejava/app/service/validation/PublishValidator.java`
- Test: `src/test/java/org/lattejava/app/tests/service/PublishValidatorTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/lattejava/app/tests/service/PublishValidatorTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module org.lattejava.app;

import org.lattejava.app.error.Errors;
import org.lattejava.app.service.validation.PublishValidator;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class PublishValidatorTest {
  public PublishValidator validator = new PublishValidator();

  @Test
  public void acceptsKeyWithinNamespace() {
    assertTrue(validator.validate("com.example", "com/example/1.0.0/lib-1.0.0.jar").empty());
  }

  @Test
  public void rejectsBlankFileName() {
    Errors errors = validator.validate("com.example", "  ");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[blank]fileName"));
  }

  @Test
  public void rejectsKeyOutsideNamespace() {
    Errors errors = validator.validate("com.example", "com/other/x.jar");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[outsideNamespace]fileName"));
  }

  @Test
  public void rejectsUncleanKey() {
    Errors errors = validator.validate("com.example", "com/example/../secret.jar");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[uncleanKey]fileName"));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.service.PublishValidatorTest`
Expected: FAIL — compile error: `PublishValidator` does not exist.

- [ ] **Step 3: Implement `PublishValidator`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import module java.base;

import org.lattejava.app.error.Errors;

/**
 * Validates a publish request body against the target group namespace. Group authorization itself is handled upstream
 * by {@link org.lattejava.app.security.PublishAuthorizer}; this only checks the shape of the requested object key.
 */
public class PublishValidator {
  /**
   * @param groupName The target namespace (already path-bound), used to derive the required key prefix.
   * @param fileName  The requested object key.
   * @return The collected errors; empty when the request is valid.
   */
  public Errors validate(String groupName, String fileName) {
    Errors errors = new Errors();
    if (fileName == null || fileName.isBlank()) {
      errors.addFieldError("fileName", "[blank]fileName", "A file name is required.");
      return errors;
    }

    String prefix = groupName.trim().toLowerCase(Locale.ROOT).replace('.', '/') + "/";
    if (!fileName.startsWith(prefix)) {
      errors.addFieldError("fileName", "[outsideNamespace]fileName",
          "The file name [%s] is not within the group namespace [%s].", fileName, groupName);
    }

    for (String segment : fileName.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        errors.addFieldError("fileName", "[uncleanKey]fileName",
            "The file name [%s] has an empty or relative path segment.", fileName);
        break;
      }
    }

    return errors;
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=org.lattejava.app.tests.service.PublishValidatorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/app/service/validation/PublishValidator.java src/test/java/org/lattejava/app/tests/service/PublishValidatorTest.java
git commit -m "Add PublishValidator for body and key validation"
```

---

### Task 6: `PublishService` and registration in `Services`

**Files:**
- Create: `src/main/java/org/lattejava/app/service/PublishService.java`
- Modify: `src/main/java/org/lattejava/app/service/Services.java`

No dedicated test — covered end-to-end by Task 9 and unit-covered via `PublishValidator` (Task 5).

- [ ] **Step 1: Create `PublishService`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.r2.R2Client;
import org.lattejava.app.r2.R2HttpClient;
import org.lattejava.app.service.validation.PublishValidator;
import org.lattejava.app.service.validation.ValidationException;
import org.lattejava.web.Configuration;

public class PublishService {
  private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(15);
  private final R2Client r2Client;
  private final PublishValidator validator;

  public PublishService(Configuration config) {
    this(new PublishValidator(), new R2HttpClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #PublishService(Configuration)} constructor instead.
   */
  public PublishService(PublishValidator validator, R2Client r2Client) {
    this.r2Client = r2Client;
    this.validator = validator;
  }

  /**
   * Validates the requested key against {@code groupName} and returns a short-lived presigned PUT URL for it.
   *
   * @param groupName The target namespace (already path-bound and authorized).
   * @param fileName  The requested object key.
   * @return The presigned PUT URL.
   * @throws ValidationException If the body/key is invalid.
   * @throws org.lattejava.app.r2.R2Exception If the URL cannot be generated.
   */
  public String createPresignedURL(String groupName, String fileName) {
    Errors errors = validator.validate(groupName, fileName);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    return r2Client.presignPut(fileName, PRESIGN_EXPIRY);
  }
}
```

- [ ] **Step 2: Register `PublishService` in `Services`**

In `Services.java`: add the field (alphabetical), the accessor (alphabetical), and the `initialize` line. The full file becomes:

```java
package org.lattejava.app.service;

import org.lattejava.web.*;

/**
 * A simple service registry.
 */
public class Services {
  private static GroupService groupService;
  private static MembershipService membershipService;
  private static PublishService publishService;
  private static VerificationService verificationService;
  private static ViewService viewService;

  public static GroupService groupService() {
    return groupService;
  }

  public static void initialize(Configuration config) {
    groupService = new GroupService(config);
    membershipService = new MembershipService(config);
    publishService = new PublishService(config);
    verificationService = new VerificationService(config);
    viewService = new ViewService(config);

    // Kick off the verification scheduled task
    verificationService.start();
  }

  public static MembershipService membershipService() {
    return membershipService;
  }

  public static PublishService publishService() {
    return publishService;
  }

  public static void shutdown() {
    verificationService.shutdown();
  }

  public static VerificationService verificationService() {
    return verificationService;
  }

  public static ViewService viewService() {
    return viewService;
  }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `latte build`
Expected: BUILD succeeds (no test yet; compilation only).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/lattejava/app/service/PublishService.java src/main/java/org/lattejava/app/service/Services.java
git commit -m "Add PublishService and register it in Services"
```

---

### Task 7: `PublishAuthorizer`

The `APIAuthorizer` decision: resolve the owning group, require VERIFIED + ACTIVE OWNER/CONTRIBUTOR membership.

**Files:**
- Create: `src/main/java/org/lattejava/app/security/PublishAuthorizer.java`

No dedicated unit test (fabricating an `HTTPRequest` + decoded `JWT` is awkward); its decision branches are covered by the end-to-end 403 cases in Task 9, and its owning-group resolution by Task 2.

- [ ] **Step 1: Create `PublishAuthorizer`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.security;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.jwt;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.Role;
import org.lattejava.app.service.GroupService;
import org.lattejava.app.service.MembershipService;
import org.lattejava.app.service.Services;
import org.lattejava.app.service.UserService;
import org.lattejava.web.oidc.APIAuthorizer;

/**
 * Decides whether a validated API caller may publish to the group named in the request path. Resolves the most
 * specific registered group that owns the namespace (see {@link GroupService#findOwningGroup(String)}), then requires
 * that group to be {@link GroupState#VERIFIED} and the caller to hold an {@link MembershipState#ACTIVE} membership in
 * it with the {@link Role#OWNER} or {@link Role#CONTRIBUTOR} role. The role test is a positive set membership so
 * future roles default to not-permitted.
 * <p>
 * Installed per-route via {@link org.lattejava.web.oidc.OIDC#apiAuthorized}, so it runs after authentication (a
 * decoded JWT is bound) and after route matching (the {@code groupName} path attribute is set).
 *
 * @author Brian Pontarelli
 */
public class PublishAuthorizer implements APIAuthorizer {
  private static final Set<Role> PUBLISH_ROLES = Set.of(Role.CONTRIBUTOR, Role.OWNER);
  private final GroupService groupService;
  private final MembershipService membershipService;

  public PublishAuthorizer() {
    this.groupService = Services.groupService();
    this.membershipService = Services.membershipService();
  }

  @Override
  public boolean authorize(HTTPRequest req, JWT jwt) {
    String groupName = (String) req.getAttribute("groupName");
    if (groupName == null || groupName.isBlank()) {
      return false;
    }

    Optional<Group> owningGroup = groupService.findOwningGroup(groupName);
    if (owningGroup.isEmpty() || owningGroup.get().state() != GroupState.VERIFIED) {
      return false;
    }

    UUID userId = UserService.toUser(jwt).userId();
    Optional<Member> member = membershipService.findMember(owningGroup.get().name(), userId);
    return member.isPresent()
        && member.get().state() == MembershipState.ACTIVE
        && PUBLISH_ROLES.contains(member.get().role());
  }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `latte build`
Expected: BUILD succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/security/PublishAuthorizer.java
git commit -m "Add PublishAuthorizer for group publish authorization"
```

---

### Task 8: `PublishController`

Parses the JSON body, calls `PublishService`, and writes JSON. Maps `ValidationException` → 400 and `R2Exception` → 500 itself (it must not let them reach `AppExceptionHandler`, which renders an HTML error page).

**Files:**
- Create: `src/main/java/org/lattejava/app/controller/PublishController.java`

End-to-end coverage is in Task 9.

- [ ] **Step 1: Create `PublishController`**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module com.fasterxml.jackson.databind;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;

import org.lattejava.app.error.Error;
import org.lattejava.app.model.PublishRequest;
import org.lattejava.app.model.PublishResponse;
import org.lattejava.app.r2.R2Exception;
import org.lattejava.app.service.PublishService;
import org.lattejava.app.service.Services;
import org.lattejava.app.service.validation.ValidationException;

/**
 * Handles {@code POST /api/v1/publish/{groupName}}: parses the JSON body, asks {@link PublishService} for a presigned
 * PUT URL, and returns it as JSON. Authentication and group authorization run upstream as middleware (see
 * {@link org.lattejava.app.security.PublishAuthorizer}); this controller only deals with the body, the presign, and
 * the JSON response. It maps its own failures to JSON status responses rather than letting them reach the HTML error
 * handler.
 *
 * @author Brian Pontarelli
 */
public class PublishController {
  private static final String GROUP_NAME = "groupName";
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final PublishService publishService;

  public PublishController() {
    this.publishService = Services.publishService();
  }

  public void publish(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);

    PublishRequest request;
    try {
      request = MAPPER.readValue(req.getInputStream(), PublishRequest.class);
    } catch (IOException e) {
      writeError(res, 400, "The request body could not be parsed as JSON.");
      return;
    }
    if (request == null) {
      writeError(res, 400, "A request body is required.");
      return;
    }

    try {
      String url = publishService.createPresignedURL(groupName, request.fileName());
      res.setStatus(200);
      res.setContentType("application/json");
      MAPPER.writeValue(res.getOutputStream(), new PublishResponse(url));
    } catch (ValidationException e) {
      writeError(res, 400, firstMessage(e, "The request is invalid."));
    } catch (R2Exception e) {
      writeError(res, 500, "The presigned URL could not be generated.");
    }
  }

  private String firstMessage(ValidationException e, String fallback) {
    return e.errors().fieldErrors.values().stream()
            .flatMap(List::stream)
            .map(error -> error.message)
            .findFirst()
            .orElse(fallback);
  }

  private void writeError(HTTPResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    MAPPER.writeValue(res.getOutputStream(), Map.of("error", message));
  }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `latte build`
Expected: BUILD succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/app/controller/PublishController.java
git commit -m "Add PublishController for the publish endpoint"
```

---

### Task 9: Wire the route and introspection endpoint in `Main`

**Files:**
- Modify: `src/main/java/org/lattejava/app/Main.java`
- Test: `src/test/java/org/lattejava/app/tests/PublishControllerTest.java`

- [ ] **Step 1: Write the failing end-to-end test**

Create `src/test/java/org/lattejava/app/tests/PublishControllerTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;
import org.lattejava.web.oidc.Tokens;

@Test
public class PublishControllerTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  private static Group verifiedGroup(String name) {
    return new Group(name, "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  }

  private WebTestAsserter publish(String groupName, String body) throws Exception {
    Tokens tokens = oidc.login("test@lattejava.org", "password", APP_ID);
    test.clearRequestState();
    return test.withHeader("Authorization", "Bearer " + tokens.accessToken())
               .withHeader("Content-Type", "application/json")
               .withBody(body)
               .post("/api/v1/publish/" + groupName);
  }

  @Test
  public void publish_ownedVerifiedGroup_returnsPresignedURL() throws Exception {
    var string = new StringBodyAsserter();
    publish("org.lattejava", "{\"fileName\":\"org/lattejava/1.0.0/lib-1.0.0.jar\"}")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("org/lattejava/1.0.0/lib-1.0.0.jar").contains("X-Amz-Signature"));
  }

  @Test
  public void publish_noOwningGroup_returns403() throws Exception {
    publish("net.unregistered.thing", "{\"fileName\":\"net/unregistered/thing/x.jar\"}")
        .assertStatus(403);
  }

  @Test
  public void publish_notAMember_returns403() throws Exception {
    db.insertGroup(verifiedGroup("com.notmine"));
    try {
      publish("com.notmine", "{\"fileName\":\"com/notmine/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteGroup("com.notmine");
    }
  }

  @Test
  public void publish_unverifiedGroup_returns403() throws Exception {
    db.insertGroup(new Group("com.pendingpub", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null));
    db.insertMember(new Member("com.pendingpub", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      publish("com.pendingpub", "{\"fileName\":\"com/pendingpub/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteMember("com.pendingpub", testUserId);
      db.deleteGroup("com.pendingpub");
    }
  }

  @Test
  public void publish_nestedGroupNotMember_returns403() throws Exception {
    db.insertGroup(verifiedGroup("com.parentpub"));
    db.insertMember(new Member("com.parentpub", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
    db.insertGroup(verifiedGroup("com.parentpub.child"));
    try {
      // Owner of com.parentpub, but com.parentpub.child is a separate group they do not belong to.
      publish("com.parentpub.child.artifact", "{\"fileName\":\"com/parentpub/child/artifact/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteGroup("com.parentpub.child");
      db.deleteMember("com.parentpub", testUserId);
      db.deleteGroup("com.parentpub");
    }
  }

  @Test
  public void publish_fileNameOutsideNamespace_returns400() throws Exception {
    publish("org.lattejava", "{\"fileName\":\"com/evil/x.jar\"}")
        .assertStatus(400);
  }

  @Test
  public void publish_missingFileName_returns400() throws Exception {
    publish("org.lattejava", "{}")
        .assertStatus(400);
  }

  @Test
  public void publish_malformedJSON_returns400() throws Exception {
    publish("org.lattejava", "not json at all")
        .assertStatus(400);
  }

  @Test
  public void publish_uncleanKey_returns400() throws Exception {
    publish("org.lattejava", "{\"fileName\":\"org/lattejava/../secret.jar\"}")
        .assertStatus(400);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=org.lattejava.app.tests.PublishControllerTest`
Expected: FAIL — the route does not exist yet, so requests return 404 (or `apiAuthenticated()` is not installed). Status assertions fail.

- [ ] **Step 3: Set the introspection endpoint on the `OIDCConfig`**

In `Main`'s constructor, add the `introspectionEndpoint(...)` call to the builder chain (FusionAuth does not advertise it via discovery):

```java
    this.oidcConfig = OIDCConfig.builder()
                                .issuer(config.get("fusionauth.issuer"))
                                .clientId(config.get("fusionauth.clientId"))
                                .clientSecret(config.get("fusionauth.clientSecret"))
                                .introspectionEndpoint(URI.create(config.get("fusionauth.baseUrl") + "/oauth2/introspect"))
                                .postLoginPage("/app/")
                                .postLogout("https://lattejava.org")
                                .build();
```

- [ ] **Step 4: Register the route**

In `Main.main()`, add a new top-level `/api` prefix block immediately after the `.prefix("/app", …)` block and before `.missingHandler(this::missing)`:

```java
       .prefix("/api", api -> {
         api.install(oidc.apiAuthenticated());
         PublishController publish = new PublishController();
         PublishAuthorizer publishAuthorizer = new PublishAuthorizer();
         api.prefix("/v1/publish", pub ->
             pub.post("/{groupName}", publish::publish, oidc.apiAuthorized(publishAuthorizer)));
       })
```

`PublishController` and `PublishAuthorizer` resolve their dependencies from `Services`, which `Main.main()` has already initialized via `Services.initialize(config)` at the top of the method. The `org.lattejava.app.controller.*` import at the top of `Main` already covers `PublishController`; add `import org.lattejava.app.security.PublishAuthorizer;` if `PublishAuthorizer` is not already resolvable (the existing `GroupSecurity` reference is constructed in-line, so confirm the `security` package is imported — add the import if needed).

- [ ] **Step 5: Run the test to verify it passes**

Run: `latte test --test=org.lattejava.app.tests.PublishControllerTest`
Expected: PASS (all nine methods).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/app/Main.java src/test/java/org/lattejava/app/tests/PublishControllerTest.java
git commit -m "Wire the publish route and introspection endpoint in Main"
```

---

### Task 10: Full suite verification

**Files:** none

- [ ] **Step 1: Run the entire test suite**

Run: `latte test`
Expected: PASS — all existing tests plus the new ones. Confirm nothing regressed (especially `GroupValidatorTest`, `GroupServiceTest`, and the flow tests).

- [ ] **Step 2: If anything fails, fix and re-run before considering the feature complete.**

No commit unless a fix was required.

---

## Self-review notes

- **Spec coverage:** route + path-bound groupName (Task 9), header tokens handled by `web` (Task 9 wiring + no app token code), authorizer with most-specific-owner + VERIFIED + ACTIVE OWNER/CONTRIBUTOR (Tasks 2, 7), validation incl. namespace containment + clean key (Task 5), presign PUT 15-min UNSIGNED-PAYLOAD (Task 3), JSON 200/400/500 from the app and 401/403/503 from `web` (Tasks 8, 9), bare-TLD short-name rejection (Task 1), tests excluding token validation/refresh (Task 9). All present.
- **Status codes:** 401/403/503 are produced by `web` middleware; the test asserts 403 (authorizer) and the app produces 200/400/500. 401/503 are not asserted here per the spec (they are `web`'s coverage).
- **Type consistency:** `findOwningGroup(List<String>)` on `DatabaseClient`, `findOwningGroup(String)` on `GroupService`; `createPresignedURL(String, String)`; `presignPut(String, Duration)`; `presignedURL(String, String, String, String, String, String, Duration, Instant)`; `validate(String, String)` on `PublishValidator`. Error codes referenced in tests (`[tld]name`, `[blank]fileName`, `[outsideNamespace]fileName`, `[uncleanKey]fileName`) match the implementations.
