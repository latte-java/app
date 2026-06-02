/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.db;

import module java.base;
import module org.lattejava.app;
import module org.testng;
import java.util.Optional;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.GroupState;
import org.lattejava.app.model.GroupVerification;
import org.lattejava.app.model.Member;
import org.lattejava.app.tests.*;

import static org.testng.Assert.*;

@Test
public class DatabaseServiceTest extends BaseTest {
  public DatabaseService databaseService;

  @AfterClass
  public void afterClass() {
    databaseService.close();
  }

  @BeforeClass
  public void beforeClass() {
    databaseService = new DatabaseService(main.config);
  }

  @Test
  public void deleteGroupRemoves() {
    Group g = new Group("test.delete.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    try {
      databaseService.deleteGroup("test.delete.fixture");
      assertTrue(databaseService.findGroup("test.delete.fixture").isEmpty());
    } finally {
      databaseService.deleteGroup("test.delete.fixture");
    }
  }

  @Test
  public void findActiveOwners_returnsOnlyOwnerActiveRows() {
    Group g = new Group("test.owners.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    UUID owner1 = UUID.fromString("aa000001-0000-0000-0000-000000000001");
    UUID owner2 = UUID.fromString("aa000001-0000-0000-0000-000000000002");
    UUID pendingOwner = UUID.fromString("aa000001-0000-0000-0000-000000000003");
    UUID contributor = UUID.fromString("aa000001-0000-0000-0000-000000000004");
    try {
      databaseService.insertMember(new Member("test.owners.fixture", owner1, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.owners.fixture", owner2, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      databaseService.insertMember(new Member("test.owners.fixture", pendingOwner, Role.OWNER, MembershipState.PENDING, null, null, null));
      databaseService.insertMember(new Member("test.owners.fixture", contributor, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(3L)));

      List<Member> owners = databaseService.findActiveOwners("test.owners.fixture");
      assertEquals(owners.size(), 2);
      assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner1)));
      assertTrue(owners.stream().anyMatch(m -> m.userId().equals(owner2)));
    } finally {
      databaseService.deleteGroup("test.owners.fixture");
    }
  }

  @Test
  public void findAncestorGroup_doesNotMatchPartialSegments() {
    Group g = new Group("test.examples", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    try {
      // 'test.example.foo' must NOT match 'test.examples' — segments must align on dots.
      assertTrue(databaseService.findAncestorGroup("test.example.foo").isEmpty());
    } finally {
      databaseService.deleteGroup("test.examples");
    }
  }

  @Test
  public void findAncestorGroup_emptyForShortName() {
    assertTrue(databaseService.findAncestorGroup("just_a_handle").isEmpty());
  }

  @Test
  public void findAncestorGroup_findsExactPrefix() {
    Group parent = new Group("test.ancestor.parent", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(parent);
    try {
      Optional<Group> found = databaseService.findAncestorGroup("test.ancestor.parent.child");
      assertTrue(found.isPresent());
      assertEquals(found.get().name(), "test.ancestor.parent");
    } finally {
      databaseService.deleteGroup("test.ancestor.parent");
    }
  }

  @Test
  public void findGroupAbsent() {
    assertTrue(databaseService.findGroup("does.not.exist.fixture").isEmpty());
  }

  @Test
  public void insertAndDeleteMember() {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID inviter = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Group g = new Group("test.member.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      Member m = new Member("test.member.fixture", userId, Role.OWNER, MembershipState.PENDING, inviter, Instant.ofEpochMilli(1714867200001L), null);
      databaseService.insertMember(m);

      Optional<Member> found = databaseService.findMember("test.member.fixture", userId);
      assertTrue(found.isPresent());
      assertEquals(found.get().groupName(), "test.member.fixture");
      assertEquals(found.get().userId(), userId);
      assertEquals(found.get().role(), Role.OWNER);
      assertEquals(found.get().state(), MembershipState.PENDING);
      assertEquals(found.get().invitedBy(), inviter);
      assertEquals(found.get().invitedAt(), Instant.ofEpochMilli(1714867200001L));
      assertNull(found.get().joinedAt());

      databaseService.deleteMember("test.member.fixture", userId);
      assertTrue(databaseService.findMember("test.member.fixture", userId).isEmpty());
    } finally {
      databaseService.deleteGroup("test.member.fixture");
    }
  }

  @Test
  public void insertAndDeleteVerification() {
    Group g = new Group("test.verify.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      GroupVerification v = new GroupVerification("test.verify.fixture", Instant.ofEpochMilli(1714867200002L), Instant.ofEpochMilli(1714867200003L));
      databaseService.insertVerification(v);

      Optional<GroupVerification> found = databaseService.findVerification("test.verify.fixture");
      assertTrue(found.isPresent());
      assertEquals(found.get().groupName(), "test.verify.fixture");
      assertEquals(found.get().startedAt(), Instant.ofEpochMilli(1714867200002L));
      assertEquals(found.get().lastCheckedAt(), Instant.ofEpochMilli(1714867200003L));

      databaseService.deleteVerification("test.verify.fixture");
      assertTrue(databaseService.findVerification("test.verify.fixture").isEmpty());
    } finally {
      databaseService.deleteGroup("test.verify.fixture");
    }
  }

  @Test
  public void insertAndFindGroup() {
    Group g = new Group("test.example.fixture", "fixture", GroupState.PENDING, "code-abc", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      Optional<Group> found = databaseService.findGroup("test.example.fixture");
      assertTrue(found.isPresent());
      assertEquals(found.get().name(), "test.example.fixture");
      assertEquals(found.get().description(), "fixture");
      assertEquals(found.get().state(), GroupState.PENDING);
      assertEquals(found.get().verificationCode(), "code-abc");
      assertEquals(found.get().createdAt(), Instant.ofEpochMilli(1714867200000L));
      assertNull(found.get().verifiedAt());
    } finally {
      databaseService.deleteGroup("test.example.fixture");
    }
  }

  @Test
  public void listGroupsForUser_emptyForUnknown() {
    UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    assertTrue(databaseService.listGroupsForUser(userId).isEmpty());
  }

  @Test
  public void listGroupsForUser_returnsActiveAndPendingMemberships() {
    UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    Group g1 = new Group("test.list.one", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    Group g2 = new Group("test.list.two", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g1);
    databaseService.insertGroup(g2);
    try {
      databaseService.insertMember(new Member("test.list.one", userId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1714867200001L)));
      databaseService.insertMember(new Member("test.list.two", userId, Role.CONTRIBUTOR, MembershipState.PENDING, userId, Instant.ofEpochMilli(1714867200002L), null));

      List<Group> groups = databaseService.listGroupsForUser(userId);
      assertEquals(groups.size(), 2);
      assertTrue(groups.stream().anyMatch(g -> g.name().equals("test.list.one")));
      assertTrue(groups.stream().anyMatch(g -> g.name().equals("test.list.two")));
    } finally {
      databaseService.deleteGroup("test.list.one");
      databaseService.deleteGroup("test.list.two");
    }
  }

  @Test
  public void listMembers_returnsAllStatesAndRoles() {
    Group g = new Group("test.list-members.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    UUID a = UUID.fromString("bb000001-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("bb000001-0000-0000-0000-000000000002");
    try {
      databaseService.insertMember(new Member("test.list-members.fixture", a, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.list-members.fixture", b, Role.CONTRIBUTOR, MembershipState.PENDING, a, Instant.ofEpochMilli(2L), null));
      List<Member> members = databaseService.listMembers("test.list-members.fixture");
      assertEquals(members.size(), 2);
    } finally {
      databaseService.deleteGroup("test.list-members.fixture");
    }
  }

  @Test
  public void listVerificationsDueForCheck_excludesRowsAboveThreshold() {
    Group g = new Group("test.notdue.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      databaseService.insertVerification(new GroupVerification("test.notdue.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867500000L)));
      // Threshold strictly less than last_checked_at means this row is not due.
      List<GroupVerification> due = databaseService.listVerificationsDueForCheck(Instant.ofEpochMilli(1714867200000L));
      assertFalse(due.stream().anyMatch(v -> v.groupName().equals("test.notdue.fixture")));
    } finally {
      databaseService.deleteGroup("test.notdue.fixture");
    }
  }

  @Test
  public void listVerificationsDueForCheck_returnsRowsBelowThreshold() {
    Group g = new Group("test.due.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      databaseService.insertVerification(new GroupVerification("test.due.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L)));
      List<GroupVerification> due = databaseService.listVerificationsDueForCheck(Instant.ofEpochMilli(1714867500000L));
      assertTrue(due.stream().anyMatch(v -> v.groupName().equals("test.due.fixture")));
    } finally {
      databaseService.deleteGroup("test.due.fixture");
    }
  }

  @Test
  public void updateGroupDescription_persistsNewValue() {
    String name = "dbtest-desc-" + UUID.randomUUID();
    databaseService.insertGroup(new Group(name, "old", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      databaseService.updateGroupDescription(name, "new description");
      Group reloaded = databaseService.findGroup(name).orElseThrow();
      assertEquals(reloaded.description(), "new description");
      assertEquals(reloaded.name(), name);
      assertEquals(reloaded.state(), GroupState.VERIFIED);
    } finally {
      databaseService.deleteGroup(name);
    }
  }

  @Test
  public void updateGroupState_changesStateAndVerifiedAt() {
    Group g = new Group("test.update.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      databaseService.updateGroupState("test.update.fixture", GroupState.VERIFIED, Instant.ofEpochMilli(1714867500000L));
      Optional<Group> after = databaseService.findGroup("test.update.fixture");
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertEquals(after.get().verifiedAt(), Instant.ofEpochMilli(1714867500000L));
    } finally {
      databaseService.deleteGroup("test.update.fixture");
    }
  }

  @Test
  public void updateMemberRole_changesRole() {
    Group g = new Group("test.role-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("cc000001-0000-0000-0000-000000000001");
    try {
      databaseService.insertMember(new Member("test.role-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.updateMemberRole("test.role-change.fixture", userId, Role.OWNER);
      Optional<Member> after = databaseService.findMember("test.role-change.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.OWNER);
    } finally {
      databaseService.deleteGroup("test.role-change.fixture");
    }
  }

  @Test
  public void updateMemberState_changesStateAndJoinedAt() {
    Group g = new Group("test.state-change.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("dd000001-0000-0000-0000-000000000001");
    try {
      databaseService.insertMember(new Member("test.state-change.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      databaseService.updateMemberState("test.state-change.fixture", userId, MembershipState.ACTIVE, Instant.ofEpochMilli(99L));
      Optional<Member> after = databaseService.findMember("test.state-change.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertEquals(after.get().joinedAt(), Instant.ofEpochMilli(99L));
    } finally {
      databaseService.deleteGroup("test.state-change.fixture");
    }
  }

  @Test
  public void updateVerificationLastChecked_updatesTimestamp() {
    Group g = new Group("test.lastcheck.fixture", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null);
    databaseService.insertGroup(g);
    try {
      databaseService.insertVerification(new GroupVerification("test.lastcheck.fixture", Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L)));
      databaseService.updateVerificationLastChecked("test.lastcheck.fixture", Instant.ofEpochMilli(1714867600000L));
      Optional<GroupVerification> after = databaseService.findVerification("test.lastcheck.fixture");
      assertTrue(after.isPresent());
      assertEquals(after.get().lastCheckedAt(), Instant.ofEpochMilli(1714867600000L));
    } finally {
      databaseService.deleteGroup("test.lastcheck.fixture");
    }
  }
}
