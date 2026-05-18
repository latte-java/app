/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.db;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import java.util.Optional;
import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
import org.lattejava.app.model.GroupVerification;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.Role;

import org.lattejava.web.Configuration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

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
    client = new DatabaseClient(config);
  }

  @Test
  public void deleteGroupRemoves() {
    Group g = new Group("test.delete.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    try {
      client.deleteGroup("test.delete.fixture");
      assertTrue(client.findGroup("test.delete.fixture").isEmpty());
    } finally {
      client.deleteGroup("test.delete.fixture");
    }
  }

  @Test
  public void findGroupAbsent() {
    assertTrue(client.findGroup("does.not.exist.fixture").isEmpty());
  }

  @Test
  public void insertAndDeleteMember() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID inviter = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Group g = new Group("test.member.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      Member m = new Member("test.member.fixture", userId, Role.OWNER, MembershipState.PENDING, inviter, Instant.ofEpochMilli(1714867200001L), null);
      client.insertMember(m);

      Optional<Member> found = client.findMember("test.member.fixture", userId);
      assertTrue(found.isPresent());
      assertEquals(found.get().groupName(), "test.member.fixture");
      assertEquals(found.get().userId(), userId);
      assertEquals(found.get().role(), Role.OWNER);
      assertEquals(found.get().state(), MembershipState.PENDING);
      assertEquals(found.get().invitedBy(), inviter);
      assertEquals(found.get().invitedAt(), Instant.ofEpochMilli(1714867200001L));
      assertNull(found.get().joinedAt());

      client.deleteMember("test.member.fixture", userId);
      assertTrue(client.findMember("test.member.fixture", userId).isEmpty());
    } finally {
      client.deleteGroup("test.member.fixture");
    }
  }

  @Test
  public void insertAndDeleteVerification() {
    Group g = new Group("test.verify.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      GroupVerification v = new GroupVerification("test.verify.fixture", Instant.ofEpochMilli(1714867200002L), Instant.ofEpochMilli(1714867200003L));
      client.insertVerification(v);

      Optional<GroupVerification> found = client.findVerification("test.verify.fixture");
      assertTrue(found.isPresent());
      assertEquals(found.get().groupName(), "test.verify.fixture");
      assertEquals(found.get().startedAt(), Instant.ofEpochMilli(1714867200002L));
      assertEquals(found.get().lastCheckedAt(), Instant.ofEpochMilli(1714867200003L));

      client.deleteVerification("test.verify.fixture");
      assertTrue(client.findVerification("test.verify.fixture").isEmpty());
    } finally {
      client.deleteGroup("test.verify.fixture");
    }
  }

  @Test
  public void insertAndFindGroup() {
    Group g = new Group("test.example.fixture", "fixture", GroupState.PENDING, "code-abc", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      Optional<Group> found = client.findGroup("test.example.fixture");
      assertTrue(found.isPresent());
      assertEquals(found.get().name(), "test.example.fixture");
      assertEquals(found.get().description(), "fixture");
      assertEquals(found.get().state(), GroupState.PENDING);
      assertEquals(found.get().verificationCode(), "code-abc");
      assertEquals(found.get().createdAt(), Instant.ofEpochMilli(1714867200000L));
      assertNull(found.get().verifiedAt());
    } finally {
      client.deleteGroup("test.example.fixture");
    }
  }

  @Test
  public void findActiveOwners_returnsOnlyOwnerActiveRows() {
    Group g = new Group("test.owners.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    UUID owner1 = UUID.fromString("aa000001-0000-0000-0000-000000000001");
    UUID owner2 = UUID.fromString("aa000001-0000-0000-0000-000000000002");
    UUID pendingOwner = UUID.fromString("aa000001-0000-0000-0000-000000000003");
    UUID contributor = UUID.fromString("aa000001-0000-0000-0000-000000000004");
    try {
      client.insertMember(new Member("test.owners.fixture", owner1, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.owners.fixture", owner2, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      client.insertMember(new Member("test.owners.fixture", pendingOwner, Role.OWNER, MembershipState.PENDING, null, null, null));
      client.insertMember(new Member("test.owners.fixture", contributor, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(3L)));

      List<Member> owners = client.findActiveOwners("test.owners.fixture");
      assertEquals(owners.size(), 2);
      assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner1)));
      assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner2)));
    } finally {
      client.deleteGroup("test.owners.fixture");
    }
  }

  @Test
  public void findAncestorGroup_doesNotMatchPartialSegments() {
    Group g = new Group("test.examples", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    try {
      // 'test.example.foo' must NOT match 'test.examples' — segments must align on dots.
      assertTrue(client.findAncestorGroup("test.example.foo").isEmpty());
    } finally {
      client.deleteGroup("test.examples");
    }
  }

  @Test
  public void findAncestorGroup_emptyForShortName() {
    assertTrue(client.findAncestorGroup("just_a_handle").isEmpty());
  }

  @Test
  public void findAncestorGroup_findsExactPrefix() {
    Group parent = new Group("test.ancestor.parent", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
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
  public void listGroupsForUser_emptyForUnknown() {
    UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    assertTrue(client.listGroupsForUser(userId).isEmpty());
  }

  @Test
  public void listGroupsForUser_returnsActiveAndPendingMemberships() {
    UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    Group g1 = new Group("test.list.one", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    Group g2 = new Group("test.list.two", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g1);
    client.insertGroup(g2);
    try {
      client.insertMember(new Member("test.list.one", userId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1714867200001L)));
      client.insertMember(new Member("test.list.two", userId, Role.CONTRIBUTOR, MembershipState.PENDING, userId, Instant.ofEpochMilli(1714867200002L), null));

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
  public void listMembers_returnsAllStatesAndRoles() {
    Group g = new Group("test.list-members.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    UUID a = UUID.fromString("bb000001-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("bb000001-0000-0000-0000-000000000002");
    try {
      client.insertMember(new Member("test.list-members.fixture", a, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.list-members.fixture", b, Role.CONTRIBUTOR, MembershipState.PENDING, a, Instant.ofEpochMilli(2L), null));
      List<Member> members = client.listMembers("test.list-members.fixture");
      assertEquals(members.size(), 2);
    } finally {
      client.deleteGroup("test.list-members.fixture");
    }
  }

  @Test
  public void listVerificationsDueForCheck_excludesRowsAboveThreshold() {
    Group g = new Group("test.notdue.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      client.insertVerification(new GroupVerification("test.notdue.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867500000L)));
      // Threshold strictly less than last_checked_at means this row is not due.
      List<GroupVerification> due = client.listVerificationsDueForCheck(Instant.ofEpochMilli(1714867200000L));
      assertFalse(due.stream().anyMatch(v -> v.groupName().equals("test.notdue.fixture")));
    } finally {
      client.deleteGroup("test.notdue.fixture");
    }
  }

  @Test
  public void listVerificationsDueForCheck_returnsRowsBelowThreshold() {
    Group g = new Group("test.due.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      client.insertVerification(new GroupVerification("test.due.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L)));
      List<GroupVerification> due = client.listVerificationsDueForCheck(Instant.ofEpochMilli(1714867500000L));
      assertTrue(due.stream().anyMatch(v -> v.groupName().equals("test.due.fixture")));
    } finally {
      client.deleteGroup("test.due.fixture");
    }
  }

  @Test
  public void selectOne() {
    D1Response response = client.query("SELECT 1 AS one");
    assertTrue(response.success(), "D1 query should succeed");
    assertEquals(response.result().getFirst().results().getFirst().get("one"), 1);
  }

  @Test
  public void updateGroupDescription_persistsNewValue() {
    String name = "dbtest-desc-" + UUID.randomUUID();
    client.insertGroup(new Group(name, "old", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      client.updateGroupDescription(name, "new description");
      Group reloaded = client.findGroup(name).orElseThrow();
      assertEquals(reloaded.description(), "new description");
      assertEquals(reloaded.name(), name);
      assertEquals(reloaded.state(), GroupState.VERIFIED);
    } finally {
      client.deleteGroup(name);
    }
  }

  @Test
  public void updateGroupState_changesStateAndVerifiedAt() {
    Group g = new Group("test.update.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      client.updateGroupState("test.update.fixture", GroupState.VERIFIED, Instant.ofEpochMilli(1714867500000L));
      Optional<Group> after = client.findGroup("test.update.fixture");
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Instant.ofEpochMilli(1714867500000L));
    } finally {
      client.deleteGroup("test.update.fixture");
    }
  }

  @Test
  public void updateMemberRole_changesRole() {
    Group g = new Group("test.role-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("cc000001-0000-0000-0000-000000000001");
    try {
      client.insertMember(new Member("test.role-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.updateMemberRole("test.role-change.fixture", userId, Role.OWNER);
      Optional<Member> after = client.findMember("test.role-change.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.OWNER);
    } finally {
      client.deleteGroup("test.role-change.fixture");
    }
  }

  @Test
  public void updateMemberState_changesStateAndJoinedAt() {
    Group g = new Group("test.state-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("dd000001-0000-0000-0000-000000000001");
    try {
      client.insertMember(new Member("test.state-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      client.updateMemberState("test.state-change.fixture", userId, MembershipState.ACTIVE, Instant.ofEpochMilli(99L));
      Optional<Member> after = client.findMember("test.state-change.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertEquals(after.get().joinedAt(), Instant.ofEpochMilli(99L));
    } finally {
      client.deleteGroup("test.state-change.fixture");
    }
  }

  @Test
  public void updateVerificationLastChecked_updatesTimestamp() {
    Group g = new Group("test.lastcheck.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    client.insertGroup(g);
    try {
      client.insertVerification(new GroupVerification("test.lastcheck.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L)));
      client.updateVerificationLastChecked("test.lastcheck.fixture", Instant.ofEpochMilli(1714867600000L));
      Optional<GroupVerification> after = client.findVerification("test.lastcheck.fixture");
      assertTrue(after.isPresent());
      assertEquals(after.get().lastCheckedAt(), Instant.ofEpochMilli(1714867600000L));
    } finally {
      client.deleteGroup("test.lastcheck.fixture");
    }
  }
}
