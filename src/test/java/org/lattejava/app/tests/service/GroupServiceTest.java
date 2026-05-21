/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;

import java.util.Optional;

import org.lattejava.app.model.Member;
import org.lattejava.app.r2.R2Client;
import org.lattejava.web.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class GroupServiceTest {
  public Configuration config;
  public DatabaseClient client;
  public GroupService service;
  public GroupValidator validator;

  private static Group input(String name, String description) {
    return new Group(name, description, GroupState.PENDING, null, Instant.EPOCH, null);
  }

  @BeforeClass
  public void beforeClass() {
    config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId",
            "r2.accessKeyId", "r2.accountId", "r2.bucket", "r2.secretAccessKey"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    TLDList tlds = new TLDList(Set.of("org", "com", "io", "dev", "net"));
    validator = new GroupValidator(client, tlds);
    service = new GroupService(config, validator, new org.lattejava.app.r2.R2HttpClient(config));
  }

  @Test
  public void createGitHub_isPendingNoCode() {
    User creator = new User(UUID.fromString("77777777-7777-7777-7777-777777777777"), "creator@example.com", "Creator");
    try {
      Group g = service.create(input("io.github.testfixture", ""), creator);
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
  public void createShortName_isVerifiedNoCode() {
    User creator = new User(UUID.fromString("55555555-5555-5555-5555-555555555555"), "creator@example.com", "Creator");
    try {
      Group g = service.create(input("test-short-fixture", "short fixture"), creator);
      assertEquals(g.name(), "test-short-fixture");
      assertEquals(g.state(), GroupState.VERIFIED);
      assertNull(g.verificationCode());
      assertNotNull(g.verifiedAt());
      assertFalse(client.findVerification("test-short-fixture").isPresent());
      Optional<Member> owner = client.findMember("test-short-fixture", creator.userId());
      assertTrue(owner.isPresent());
      assertEquals(owner.get().role(), Role.OWNER);
      assertEquals(owner.get().state(), MembershipState.ACTIVE);
    } finally {
      client.deleteGroup("test-short-fixture");
    }
  }

  @Test
  public void delete_bucketNotEmpty_throws() {
    // Role-based authorization is enforced by GroupSecurity middleware at the route layer, not by GroupService.
    Group g = new Group("test.delete.hasartifacts", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    R2Client fakeR2 = _ -> false; // not empty
    GroupService localService = new GroupService(config, validator, fakeR2);
    try {
      expectThrows(ValidationException.class, () -> localService.delete(g));
    } finally {
      client.deleteGroup("test.delete.hasartifacts");
    }
  }

  @Test
  public void delete_emptyBucket_succeeds() {
    Group g = new Group("test.delete.empty", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    R2Client fakeR2 = _ -> true; // empty
    GroupService localService = new GroupService(config, validator, fakeR2);
    try {
      localService.delete(g);
      assertFalse(client.findGroup("test.delete.empty").isPresent());
    } catch (RuntimeException e) {
      client.deleteGroup("test.delete.empty");
      throw e;
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
      client.deleteGroup("org.testfilter.blankone");
      client.deleteGroup("org.testfilter.blanktwo");
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
      client.deleteGroup("org.testfilter.casey");
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
      client.deleteGroup("org.testfilter.wildone");
      client.deleteGroup("org.testfilter.wildtwo");
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
      client.deleteGroup("org.testfilter.alpha");
      client.deleteGroup("org.testfilter.beta");
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
      client.deleteGroup("org.testfilter.solo");
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
      client.deleteGroup("test-list-for-user-fixture");
    }
  }

  @Test
  public void updateDescription_allowsEmpty() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999998"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.emptydesc", "had a description"), creator);
    try {
      service.updateDescription(g, "");
      assertEquals(client.findGroup("io.github.emptydesc").orElseThrow().description(), "");
    } finally {
      client.deleteGroup("io.github.emptydesc");
    }
  }

  @Test
  public void updateDescription_persistsTrimmedValue() {
    User creator = new User(UUID.fromString("99999999-9999-9999-9999-999999999996"), "creator@example.com", "Creator");
    Group g = service.create(input("io.github.updatedesc", "original"), creator);
    try {
      service.updateDescription(g, "  updated value  ");
      assertEquals(client.findGroup("io.github.updatedesc").orElseThrow().description(), "updated value");
    } finally {
      client.deleteGroup("io.github.updatedesc");
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
      assertEquals(client.findGroup("io.github.toolong").orElseThrow().description(), "original");
    } finally {
      client.deleteGroup("io.github.toolong");
    }
  }
}
