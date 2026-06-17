/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.fusionauth;
import module org.lattejava.web;
import java.util.Optional;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.User;
import org.lattejava.app.tests.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Test
public class MembershipServiceTest extends BaseTest {
  public DatabaseService databaseService;
  public MembershipService service;

  @Test
  public void accept_Invitation_active_isNoOp() {
    Group g = new Group("test.accept.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000002");
    try {
      Instant joinedAt = Instant.ofEpochMilli(50L);
      databaseService.insertMember(new Member("test.accept.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, joinedAt));
      service.acceptInvitation("test.accept.active", userId);
      Optional<Member> after = databaseService.findMember("test.accept.active", userId);
      assertEquals(after.get().joinedAt(), joinedAt);
    } finally {
      databaseService.deleteGroup("test.accept.active");
    }
  }

  @Test
  public void accept_Invitation_pending_marksActive() {
    Group g = new Group("test.accept.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000001");
    try {
      databaseService.insertMember(new Member("test.accept.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      service.acceptInvitation("test.accept.fixture", userId);
      Optional<Member> after = databaseService.findMember("test.accept.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertNotNull(after.get().joinedAt());
    } finally {
      databaseService.deleteGroup("test.accept.fixture");
    }
  }

  @BeforeClass
  public void beforeClass() {
    databaseService = new DatabaseService(main.config);
    service = new MembershipService(databaseService, main.config);
  }

  @Test
  public void changeRole_demoteLastActiveOwner_throws() {
    Group g = new Group("test.role.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000003");
    UUID lastOwner = UUID.fromString("33000001-0000-0000-0000-000000000004");
    try {
      databaseService.insertMember(new Member("test.role.lastowner", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.role.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      User currentUser = new User(current, "a@x", "A");
      service.changeRole("test.role.lastowner", lastOwner, Role.CONTRIBUTOR, currentUser);
      UUID admin = UUID.fromString("33000001-0000-0000-0000-000000000005");
      User adminUser = new User(admin, "admin@x", "Admin");
      expectThrows(ValidationException.class,
          () -> service.changeRole("test.role.lastowner", current, Role.CONTRIBUTOR, adminUser));
    } finally {
      databaseService.deleteGroup("test.role.lastowner");
    }
  }

  @Test
  public void changeRole_promote_succeeds() {
    Group g = new Group("test.role.promote", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000001");
    UUID target = UUID.fromString("33000001-0000-0000-0000-000000000002");
    try {
      databaseService.insertMember(new Member("test.role.promote", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.role.promote", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.changeRole("test.role.promote", target, Role.OWNER, new User(current, "a@x", "A"));
      Optional<Member> after = databaseService.findMember("test.role.promote", target);
      assertEquals(after.get().role(), Role.OWNER);
    } finally {
      databaseService.deleteGroup("test.role.promote");
    }
  }

  @Test
  public void changeRole_self_throws() {
    Group g = new Group("test.role.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000006");
    try {
      databaseService.insertMember(new Member("test.role.self", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(current, "a@x", "A");
      expectThrows(ValidationException.class,
          () -> service.changeRole("test.role.self", current, Role.CONTRIBUTOR, currentUser));
    } finally {
      databaseService.deleteGroup("test.role.self");
    }
  }

  @Test
  public void decline_Invitation_active_isNoOp() {
    Group g = new Group("test.decline.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000002");
    try {
      databaseService.insertMember(new Member("test.decline.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      service.declineInvitation("test.decline.active", userId);
      assertTrue(databaseService.findMember("test.decline.active", userId).isPresent());
    } finally {
      databaseService.deleteGroup("test.decline.active");
    }
  }

  @Test
  public void decline_Invitation_pending_deletesRow() {
    Group g = new Group("test.decline.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000001");
    try {
      databaseService.insertMember(new Member("test.decline.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      service.declineInvitation("test.decline.fixture", userId);
      assertTrue(databaseService.findMember("test.decline.fixture", userId).isEmpty());
    } finally {
      databaseService.deleteGroup("test.decline.fixture");
    }
  }

  @Test
  public void invite_alreadyMember_throwsValidation() {
    Group g = new Group("test.invite.dup", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000003");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    String uniqueEmail = "test+invite-dup-" + UUID.randomUUID() + "@lattejava.org";
    try {
      service.invite(new InviteRequest("test.invite.dup", uniqueEmail, Role.CONTRIBUTOR), inviter);
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.invite(new InviteRequest("test.invite.dup", uniqueEmail, Role.CONTRIBUTOR), inviter)
      );
      assertTrue(ex.errors().containsError("[alreadyInvited]"));
    } finally {
      databaseService.deleteGroup("test.invite.dup");
    }
  }

  @Test
  public void invite_blankEmail_throwsValidation() {
    Group g = new Group("test.invite.blank", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000002");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    try {
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.invite(new InviteRequest("test.invite.blank", "  ", Role.CONTRIBUTOR), inviter)
      );
      assertNotNull(ex.errors().getFieldError("email", "[blank]email"));
    } finally {
      databaseService.deleteGroup("test.invite.blank");
    }
  }

  @Test
  public void invite_newEmail_createsFAUserAndPendingMember() {
    Group g = new Group("test.invite.new", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000001");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    String uniqueEmail = "test+invite-new-" + UUID.randomUUID() + "@lattejava.org";
    try {
      Member m = service.invite(new InviteRequest("test.invite.new", uniqueEmail, Role.CONTRIBUTOR), inviter);
      assertNotNull(m);
      assertEquals(m.groupName(), "test.invite.new");
      assertEquals(m.role(), Role.CONTRIBUTOR);
      assertEquals(m.state(), MembershipState.PENDING);
      assertEquals(m.invitedBy(), inviterId);
      assertNotNull(m.invitedAt());
      assertNull(m.joinedAt());

      Optional<Member> persisted = databaseService.findMember("test.invite.new", m.userId());
      assertTrue(persisted.isPresent());
    } finally {
      databaseService.deleteGroup("test.invite.new");
    }
  }

  @Test
  public void listMembersEnrichesUserFromFusionAuth() {
    FusionAuthClient fa = new FusionAuthClient(main.config.get("fusionauth.apiKey"), main.config.get("fusionauth.baseUrl"));
    UUID testUserId = fa.retrieveUser(null, null, null, null, "test@lattejava.org", null, null).user().id();

    databaseService.deleteGroup("test.enrich.fixture"); // cascades to members
    databaseService.insertGroup(new Group("test.enrich.fixture", "Enrich fixture", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L)));
    databaseService.insertMember(new Member("test.enrich.fixture", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));

    try {
      List<Member> members = service.listMembers("test.enrich.fixture");

      assertEquals(members.size(), 1);
      assertEquals(members.getFirst().user().userId(), testUserId);
      assertEquals(members.getFirst().user().email(), "test@lattejava.org");
      assertEquals(members.getFirst().user().username(), "OrdinaryUser");
    } finally {
      databaseService.deleteGroup("test.enrich.fixture");
    }
  }

  @Test
  public void leave_contributor_succeeds() {
    Group g = new Group("test.leave.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000001");
    UUID leaver = UUID.fromString("44000001-0000-0000-0000-000000000002");
    try {
      databaseService.insertMember(new Member("test.leave.contrib", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.leave.contrib", leaver, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.leave("test.leave.contrib", new User(leaver, "l@x", "L"));
      assertTrue(databaseService.findMember("test.leave.contrib", leaver).isEmpty());
    } finally {
      databaseService.deleteGroup("test.leave.contrib");
    }
  }

  @Test
  public void leave_lastActiveOwner_throws() {
    Group g = new Group("test.leave.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000003");
    try {
      databaseService.insertMember(new Member("test.leave.lastowner", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User ownerUser = new User(owner, "o@x", "O");
      expectThrows(ValidationException.class, () -> service.leave("test.leave.lastowner", ownerUser));
    } finally {
      databaseService.deleteGroup("test.leave.lastowner");
    }
  }

  @Test
  public void leave_pendingInvitee_deletesRow() {
    // Leaving a group as a PENDING invitee is allowed and equivalent to declining the invitation — the row is
    // deleted with no last-owner check (PENDING rows are never "the last active owner").
    Group g = new Group("test.leave.pending", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000004");
    UUID invitee = UUID.fromString("44000001-0000-0000-0000-000000000005");
    try {
      databaseService.insertMember(new Member("test.leave.pending", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.leave.pending", invitee, Role.CONTRIBUTOR, MembershipState.PENDING, owner, Instant.ofEpochMilli(2L), null));
      service.leave("test.leave.pending", new User(invitee, "i@x", "I"));
      assertTrue(databaseService.findMember("test.leave.pending", invitee).isEmpty());
    } finally {
      databaseService.deleteGroup("test.leave.pending");
    }
  }

  @Test
  public void remove_contributor_succeeds() {
    Group g = new Group("test.remove.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID current = UUID.fromString("22000001-0000-0000-0000-000000000001");
    UUID target = UUID.fromString("22000001-0000-0000-0000-000000000002");
    try {
      databaseService.insertMember(new Member("test.remove.contrib", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      databaseService.insertMember(new Member("test.remove.contrib", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.remove("test.remove.contrib", target, new User(current, "actor@lattejava.org", "Actor"));
      assertTrue(databaseService.findMember("test.remove.contrib", target).isEmpty());
    } finally {
      databaseService.deleteGroup("test.remove.contrib");
    }
  }

  @Test
  public void remove_self_throws() {
    Group g = new Group("test.remove.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID current = UUID.fromString("22000001-0000-0000-0000-000000000005");
    try {
      databaseService.insertMember(new Member("test.remove.self", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(current, "actor@lattejava.org", "Actor");
      expectThrows(ValidationException.class, () -> service.remove("test.remove.self", current, currentUser));
    } finally {
      databaseService.deleteGroup("test.remove.self");
    }
  }

  @Test
  public void remove_wouldOrphanGroup_isPrevented() {
    Group g = new Group("test.remove.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    databaseService.insertGroup(g);
    UUID lastOwner = UUID.fromString("22000001-0000-0000-0000-000000000006");
    UUID adminActor = UUID.fromString("22000001-0000-0000-0000-000000000007");
    try {
      databaseService.insertMember(new Member("test.remove.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(adminActor, "actor@lattejava.org", "Actor");
      expectThrows(ValidationException.class, () -> service.remove("test.remove.lastowner", lastOwner, currentUser));
    } finally {
      databaseService.deleteGroup("test.remove.lastowner");
    }
  }
}
