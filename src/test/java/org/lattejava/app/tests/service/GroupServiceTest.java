/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;

import java.util.Optional;

import org.lattejava.app.model.Member;
import org.lattejava.app.s3.S3Client;
import org.lattejava.app.tests.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class GroupServiceTest extends BaseTest {
  public DatabaseService databaseService;
  public GroupService service;
  public GroupValidator validator;

  private static Group input(String name, String description) {
    return new Group(name, description, GroupState.PENDING, null, Instant.EPOCH, null);
  }

  @BeforeClass
  public void beforeClass() {
    databaseService = new DatabaseService(main.config);
    TLDList tlds = new TLDList(Set.of("org", "com", "io", "dev", "net"));
    validator = new GroupValidator(databaseService, tlds);
    service = new GroupService(databaseService, validator, new org.lattejava.app.s3.S3HttpClient(main.config));
  }

  @Test
  public void createGitHub_isPendingNoCode() {
    User creator = new User(UUID.fromString("77777777-7777-7777-7777-777777777777"), "creator@example.com", "Creator");
    try {
      Group g = service.create(input("io.github.testfixture", ""), creator);
      assertEquals(g.state(), GroupState.PENDING);
      assertNull(g.verificationCode());
      assertFalse(databaseService.findVerification("io.github.testfixture").isPresent());
      assertTrue(databaseService.findMember("io.github.testfixture", creator.userId()).isPresent());
    } finally {
      databaseService.deleteGroup("io.github.testfixture");
    }
  }

  @Test
  public void createInvalid_throws() {
    User creator = new User(UUID.fromString("88888888-8888-8888-8888-888888888888"), "creator@example.com", "Creator");
    ValidationException ex = expectThrows(
        ValidationException.class,
        () -> service.create(input("example.org", "forward DNS not allowed"), creator)
    );
    assertFalse(ex.errors().empty());
    assertNotNull(ex.errors().getFieldError("name", "[unknownTld]name"));
  }

  @Test
  public void createReverseDNS_isPendingWithCode() {
    User creator = new User(UUID.fromString("66666666-6666-6666-6666-666666666666"), "creator@example.com", "Creator");
    try {
      Group g = service.create(input("org.testfixture", "reverse-dns fixture"), creator);
      assertEquals(g.name(), "org.testfixture");
      assertEquals(g.state(), GroupState.PENDING);
      assertNotNull(g.verificationCode());
      assertEquals(g.verificationCode().length(), 32);
      assertNull(g.verifiedAt());
      assertTrue(databaseService.findVerification("org.testfixture").isPresent());
      Optional<Member> owner = databaseService.findMember("org.testfixture", creator.userId());
      assertTrue(owner.isPresent());
      assertEquals(owner.get().role(), Role.OWNER);
      assertEquals(owner.get().state(), MembershipState.ACTIVE);
    } finally {
      databaseService.deleteGroup("org.testfixture");
    }
  }

  @Test
  public void createShortName_isVerifiedNoCode() {
    User creator = new User(UUID.fromString("55555555-5555-5555-5555-555555555555"), "creator@example.com", "Creator");
    try {
      Group g = service.create(input("test-short-fixture", "short fixture"), creator);
      assertEquals(g.name(), "test-short-fixture");
      assertEquals(g.state(), GroupState.VERIFIED);
      assertNull(g.verificationCode());
      assertNotNull(g.verifiedAt());
      assertFalse(databaseService.findVerification("test-short-fixture").isPresent());
      Optional<Member> owner = databaseService.findMember("test-short-fixture", creator.userId());
      assertTrue(owner.isPresent());
      assertEquals(owner.get().role(), Role.OWNER);
      assertEquals(owner.get().state(), MembershipState.ACTIVE);
    } finally {
      databaseService.deleteGroup("test-short-fixture");
    }
  }

  @Test
  public void delete_bucketNotEmpty_throws() {
    // Role-based authorization is enforced by GroupSecurity middleware at the route layer, not by GroupService.
    Group g = new Group("test.delete.hasartifacts", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    S3Client fakeS3 = new S3Client() {
      public boolean isPrefixEmpty(String prefix) {
        return false; // not empty
      }

      public List<String> listKeys(String prefix) {
        throw new UnsupportedOperationException("not used in this test");
      }

      public String presignPut(String key, Duration expiry) {
        throw new UnsupportedOperationException("not used in this test");
      }
    };
    GroupService localService = new GroupService(databaseService, validator, fakeS3);
    try {
      expectThrows(ValidationException.class, () -> localService.delete(g));
    } finally {
      databaseService.deleteGroup("test.delete.hasartifacts");
    }
  }

  @Test
  public void delete_emptyBucket_succeeds() {
    Group g = new Group("test.delete.empty", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    S3Client fakeS3 = new S3Client() {
      public boolean isPrefixEmpty(String prefix) {
        return true; // empty
      }

      public List<String> listKeys(String prefix) {
        throw new UnsupportedOperationException("not used in this test");
      }

      public String presignPut(String key, Duration expiry) {
        throw new UnsupportedOperationException("not used in this test");
      }
    };
    GroupService localService = new GroupService(databaseService, validator, fakeS3);
    try {
      localService.delete(g);
      assertFalse(databaseService.findGroup("test.delete.empty").isPresent());
    } catch (RuntimeException e) {
      databaseService.deleteGroup("test.delete.empty");
      throw e;
    }
  }

  @Test
  public void findOwningGroup_picksMostSpecificRegisteredAncestor() {
    Group parent = new Group("com.owntest", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    Group child = new Group("com.owntest.child", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(parent);
    databaseService.insertGroup(child);
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
      databaseService.deleteGroup("com.owntest.child");
      databaseService.deleteGroup("com.owntest");
    }
  }

  @Test
  public void findOwningGroup_shortNameExactMatch() {
    Group shortGroup = new Group("mylibtest", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(shortGroup);
    try {
      assertEquals(service.findOwningGroup("mylibtest").map(Group::name).orElse(null), "mylibtest");
      assertTrue(service.findOwningGroup("unregisteredshortname").isEmpty());
    } finally {
      databaseService.deleteGroup("mylibtest");
    }
  }

  @Test
  public void listForUser_filterBlankReturnsAll() {
    User creator = new User(UUID.fromString("aabbccdd-1111-1111-1111-111111111111"), "creator@example.com", "Creator");
    try {
      service.create(input("org.testfilter.blankone", ""), creator);
      service.create(input("org.testfilter.blanktwo", ""), creator);

      List<Group> whitespace = service.listForUser(creator, "   ");
      assertEquals(whitespace.size(), 2);

      List<Group> nullFilter = service.listForUser(creator, null);
      assertEquals(nullFilter.size(), 2);
    } finally {
      databaseService.deleteGroup("org.testfilter.blankone");
      databaseService.deleteGroup("org.testfilter.blanktwo");
    }
  }

  @Test
  public void listForUser_filterCaseInsensitive() {
    User creator = new User(UUID.fromString("aabbccdd-2222-2222-2222-222222222222"), "creator@example.com", "Creator");
    try {
      service.create(input("org.testfilter.casey", ""), creator);

      // Group names are lowercased on insert; the service lowercases the filter before matching.
      List<Group> groups = service.listForUser(creator, "CASEY");
      assertTrue(groups.stream().anyMatch(g -> g.name().equals("org.testfilter.casey")));
    } finally {
      databaseService.deleteGroup("org.testfilter.casey");
    }
  }

  @Test
  public void listForUser_filterEscapesLikeWildcards() {
    User creator = new User(UUID.fromString("aabbccdd-3333-3333-3333-333333333333"), "creator@example.com", "Creator");
    try {
      service.create(input("org.testfilter.wildone", ""), creator);
      service.create(input("org.testfilter.wildtwo", ""), creator);

      // [%] is a SQL LIKE wildcard. Without escaping, the filter would match every row. With escaping, it must
      // be matched literally — and group names cannot contain a literal [%].
      List<Group> percent = service.listForUser(creator, "%");
      assertTrue(percent.isEmpty(), "filter [%] must not act as a wildcard");

      // [_] matches any single character in LIKE. Escaped, only literal underscores match — and DNS labels do
      // not permit underscores, so no group name can match.
      List<Group> underscore = service.listForUser(creator, "_");
      assertTrue(underscore.isEmpty(), "filter [_] must not act as a wildcard");
    } finally {
      databaseService.deleteGroup("org.testfilter.wildone");
      databaseService.deleteGroup("org.testfilter.wildtwo");
    }
  }

  @Test
  public void listForUser_filterMatchesSubstring() {
    User creator = new User(UUID.fromString("aabbccdd-4444-4444-4444-444444444444"), "creator@example.com", "Creator");
    try {
      service.create(input("org.testfilter.alpha", ""), creator);
      service.create(input("org.testfilter.beta", ""), creator);

      List<Group> groups = service.listForUser(creator, "alpha");
      assertEquals(groups.size(), 1);
      assertEquals(groups.getFirst().name(), "org.testfilter.alpha");
    } finally {
      databaseService.deleteGroup("org.testfilter.alpha");
      databaseService.deleteGroup("org.testfilter.beta");
    }
  }

  @Test
  public void listForUser_filterNoMatch() {
    User creator = new User(UUID.fromString("aabbccdd-5555-5555-5555-555555555555"), "creator@example.com", "Creator");
    try {
      service.create(input("org.testfilter.solo", ""), creator);

      List<Group> groups = service.listForUser(creator, "zzz-does-not-exist");
      assertTrue(groups.isEmpty());
    } finally {
      databaseService.deleteGroup("org.testfilter.solo");
    }
  }

  @Test
  public void listForUser_returnsCreatedGroup() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999999"), "creator@example.com", "Creator");
    try {
      service.create(input("test-list-for-user-fixture", ""), creator);
      List<Group> groups = service.listForUser(creator);
      assertTrue(groups.stream().anyMatch(g -> g.name().equals("test-list-for-user-fixture")));
    } finally {
      databaseService.deleteGroup("test-list-for-user-fixture");
    }
  }

  @Test
  public void updateDescription_allowsEmpty() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999998"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.emptydesc", "had a description"), creator);
    try {
      service.updateDescription(g, "");
      assertEquals(databaseService.findGroup("io.github.emptydesc").orElseThrow().description(), "");
    } finally {
      databaseService.deleteGroup("io.github.emptydesc");
    }
  }

  @Test
  public void updateDescription_persistsTrimmedValue() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999996"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.updatedesc", "original"), creator);
    try {
      service.updateDescription(g, "  updated value  ");
      assertEquals(databaseService.findGroup("io.github.updatedesc").orElseThrow().description(), "updated value");
    } finally {
      databaseService.deleteGroup("io.github.updatedesc");
    }
  }

  @Test
  public void updateDescription_rejectsTooLong() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999997"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.toolong", "original"), creator);
    try {
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.updateDescription(g, "x".repeat(501))
      );
      assertNotNull(ex.errors().getFieldError("description", "[tooLong]description"));
      assertEquals(databaseService.findGroup("io.github.toolong").orElseThrow().description(), "original");
    } finally {
      databaseService.deleteGroup("io.github.toolong");
    }
  }
}
