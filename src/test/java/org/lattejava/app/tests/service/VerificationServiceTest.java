/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import java.util.Optional;

import org.lattejava.app.tests.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Test
public class VerificationServiceTest extends BaseTest {
  public DatabaseService databaseService;
  public FakeDNSResolver resolver;
  public VerificationService service;

  @BeforeClass
  public void beforeClass() {
    databaseService = new DatabaseService(main.config);
    resolver = new FakeDNSResolver();
    service = new VerificationService(databaseService, main.config, resolver, new GitHubHTTPClient());
  }

  @Test
  public void checkOne_matchesTxt_marksVerifiedAndDeletesRow() {
    Group g = new Group("test.checkone.match", "", GroupState.PENDING, "code-match", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.match", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertVerification(v);
    resolver.responses.put("match.checkone.test", List.of("code-match"));
    try {
      service.checkOne(v, Instant.ofEpochMilli(100L));

      Optional<Group> after = databaseService.findGroup("test.checkone.match");
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Instant.ofEpochMilli(100L));
      assertFalse(databaseService.findVerification("test.checkone.match").isPresent());
    } finally {
      databaseService.deleteGroup("test.checkone.match");
    }
  }

  @Test
  public void checkOne_noMatch_updatesLastCheckedOnly() {
    Group g = new Group("test.checkone.miss", "", GroupState.PENDING, "code-miss", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.miss", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertVerification(v);
    resolver.responses.put("miss.checkone.test", List.of("not-the-code"));
    try {
      service.checkOne(v, Instant.ofEpochMilli(50L));

      Optional<Group> after = databaseService.findGroup("test.checkone.miss");
      assertEquals(after.get().state(), GroupState.PENDING);
      assertNull(after.get().verifiedAt());
      Optional<GroupVerification> verAfter = databaseService.findVerification("test.checkone.miss");
      assertTrue(verAfter.isPresent());
      assertEquals(verAfter.get().lastCheckedAt(), Instant.ofEpochMilli(50L));
    } finally {
      databaseService.deleteGroup("test.checkone.miss");
    }
  }

  @Test
  public void checkOne_pastDeadline_marksFailedAndDeletesRow() {
    Instant started = Instant.ofEpochMilli(1L);
    Instant deadline = started.plus(Duration.ofHours(48));
    Group g = new Group("test.checkone.late", "", GroupState.PENDING, "code-late", started, null);
    databaseService.insertGroup(g);
    GroupVerification v = new GroupVerification("test.checkone.late", started, started);
    databaseService.insertVerification(v);
    // No DNS match — but deadline has elapsed.
    try {
      service.checkOne(v, deadline.plusMillis(1));

      Optional<Group> after = databaseService.findGroup("test.checkone.late");
      assertEquals(after.get().state(), GroupState.FAILED);
      assertNull(after.get().verifiedAt());
      assertFalse(databaseService.findVerification("test.checkone.late").isPresent());
    } finally {
      databaseService.deleteGroup("test.checkone.late");
    }
  }

  @Test
  public void check_failedGroup_nowResolving_marksVerified() {
    Group g = new Group("test.check.recovered", "", GroupState.FAILED, "code-recovered", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    resolver.responses.put("recovered.check.test", List.of("code-recovered"));
    try {
      service.check("test.check.recovered", Instant.ofEpochMilli(500L));

      Optional<Group> after = databaseService.findGroup("test.check.recovered");
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Instant.ofEpochMilli(500L));
      assertFalse(databaseService.findVerification("test.check.recovered").isPresent());
    } finally {
      databaseService.deleteGroup("test.check.recovered");
    }
  }

  @Test
  public void check_failedGroup_stillNotResolving_resetsToPendingWithFreshVerification() {
    Group g = new Group("test.check.failed", "", GroupState.FAILED, "code-failed", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    resolver.responses.put("failed.check.test", List.of("not-the-code"));
    try {
      service.check("test.check.failed", Instant.ofEpochMilli(500L));

      Optional<Group> after = databaseService.findGroup("test.check.failed");
      assertEquals(after.get().state(), GroupState.PENDING);
      assertNull(after.get().verifiedAt());
      Optional<GroupVerification> ver = databaseService.findVerification("test.check.failed");
      assertTrue(ver.isPresent());
      assertEquals(ver.get().startedAt(), Instant.ofEpochMilli(500L));
      assertEquals(ver.get().lastCheckedAt(), Instant.ofEpochMilli(500L));
    } finally {
      databaseService.deleteGroup("test.check.failed");
    }
  }

  @Test
  public void check_missingGroup_isNoOp() {
    service.check("test.check.nonexistent", Instant.ofEpochMilli(500L));

    assertFalse(databaseService.findGroup("test.check.nonexistent").isPresent());
    assertFalse(databaseService.findVerification("test.check.nonexistent").isPresent());
  }

  @Test
  public void check_pendingGroupWithRecord_runsCheckOneAgainstExisting() {
    Group g = new Group("test.check.pending", "", GroupState.PENDING, "code-pending", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    databaseService.insertVerification(new GroupVerification("test.check.pending", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    resolver.responses.put("pending.check.test", List.of("code-pending"));
    try {
      service.check("test.check.pending", Instant.ofEpochMilli(500L));

      Optional<Group> after = databaseService.findGroup("test.check.pending");
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Instant.ofEpochMilli(500L));
      assertFalse(databaseService.findVerification("test.check.pending").isPresent());
    } finally {
      databaseService.deleteGroup("test.check.pending");
    }
  }

  @Test
  public void check_verifiedGroup_isNoOp() {
    Group g = new Group("test.check.verified", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    try {
      service.check("test.check.verified", Instant.ofEpochMilli(500L));

      Optional<Group> after = databaseService.findGroup("test.check.verified");
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertFalse(databaseService.findVerification("test.check.verified").isPresent());
    } finally {
      databaseService.deleteGroup("test.check.verified");
    }
  }

  @BeforeMethod
  public void resetResolver() {
    resolver.responses.clear();
    resolver.throwingHosts.clear();
  }

  @Test
  public void scan_isolatesPerVerificationFailures() {
    Group g1 = new Group("test.scan.iso1", "", GroupState.PENDING, "code-iso1", Instant.ofEpochMilli(1L), null);
    Group g2 = new Group("test.scan.iso2", "", GroupState.PENDING, "code-iso2", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g1);
    databaseService.insertGroup(g2);
    databaseService.insertVerification(new GroupVerification("test.scan.iso1", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    databaseService.insertVerification(new GroupVerification("test.scan.iso2", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    resolver.responses.put("iso2.scan.test", List.of("code-iso2"));
    resolver.throwingHosts.add("iso1.scan.test");
    try {
      service.scan(Instant.ofEpochMilli(200L));

      Optional<Group> iso2 = databaseService.findGroup("test.scan.iso2");
      assertEquals(iso2.get().state(), GroupState.VERIFIED);
    } finally {
      databaseService.deleteGroup("test.scan.iso1");
      databaseService.deleteGroup("test.scan.iso2");
    }
  }

  @Test
  public void scan_processesAllDueVerifications() {
    Group g1 = new Group("test.scan.one", "", GroupState.PENDING, "code-one", Instant.ofEpochMilli(1L), null);
    Group g2 = new Group("test.scan.two", "", GroupState.PENDING, "code-two", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g1);
    databaseService.insertGroup(g2);
    databaseService.insertVerification(new GroupVerification("test.scan.one", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    databaseService.insertVerification(new GroupVerification("test.scan.two", Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    resolver.responses.put("one.scan.test", List.of("code-one"));
    // 'two' has no matching TXT response.
    try {
      service.scan(Instant.ofEpochMilli(200L));

      Optional<Group> one = databaseService.findGroup("test.scan.one");
      assertEquals(one.get().state(), GroupState.VERIFIED);
      assertFalse(databaseService.findVerification("test.scan.one").isPresent());

      Optional<Group> two = databaseService.findGroup("test.scan.two");
      assertEquals(two.get().state(), GroupState.PENDING);
      Optional<GroupVerification> twoVer = databaseService.findVerification("test.scan.two");
      assertTrue(twoVer.isPresent());
      assertEquals(twoVer.get().lastCheckedAt(), Instant.ofEpochMilli(200L));
    } finally {
      databaseService.deleteGroup("test.scan.one");
      databaseService.deleteGroup("test.scan.two");
    }
  }

  @Test
  public void verifyGitHub_notGitHubKind_throws() {
    Group g = new Group("test.notgithub", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1L), null);
    databaseService.insertGroup(g);
    try {
      User current = new User(UUID.randomUUID(), "u@x", "U");
      expectThrows(ValidationException.class, () -> service.verifyGitHub(g, current));
    } finally {
      databaseService.deleteGroup("test.notgithub");
    }
  }

  // Inner fake — keyed by host (forward-DNS apex), value is the list of TXT strings to return.
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
}
