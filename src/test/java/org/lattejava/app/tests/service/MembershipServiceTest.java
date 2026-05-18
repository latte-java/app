/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;
import java.util.Optional;

import org.lattejava.app.model.Member;
import org.lattejava.web.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Test
public class MembershipServiceTest {
  public DatabaseClient client;
  public Configuration config;
  public MembershipService service;

  @Test
  public void accept_Invitation_active_isNoOp() {
    Group g = new Group("test.accept.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000002");
    try {
      Instant joinedAt = Instant.ofEpochMilli(50L);
      client.insertMember(new Member("test.accept.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, joinedAt));
      service.acceptInvitation("test.accept.active", userId);
      Optional<Member> after = client.findMember("test.accept.active", userId);
      assertEquals(after.get().joinedAt(), joinedAt);
    } finally {
      client.deleteGroup("test.accept.active");
    }
  }

  @Test
  public void accept_Invitation_pending_marksActive() {
    Group g = new Group("test.accept.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("ff000001-0000-0000-0000-000000000001");
    try {
      client.insertMember(new Member("test.accept.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      service.acceptInvitation("test.accept.fixture", userId);
      Optional<Member> after = client.findMember("test.accept.fixture", userId);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertNotNull(after.get().joinedAt());
    } finally {
      client.deleteGroup("test.accept.fixture");
    }
  }

  @BeforeClass
  public void beforeClass() {
    config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId",
            "fusionauth.apiKey", "fusionauth.baseUrl"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    service = new MembershipService(config);
  }

  @Test
  public void changeRole_demoteLastActiveOwner_throws() {
    Group g = new Group("test.role.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000003");
    UUID lastOwner = UUID.fromString("33000001-0000-0000-0000-000000000004");
    try {
      client.insertMember(new Member("test.role.lastowner", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.role.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      User currentUser = new User(current, "a@x", "A");
      service.changeRole("test.role.lastowner", lastOwner, Role.CONTRIBUTOR, currentUser);
      UUID admin = UUID.fromString("33000001-0000-0000-0000-000000000005");
      User adminUser = new User(admin, "admin@x", "Admin");
      expectThrows(ValidationException.class,
          () -> service.changeRole("test.role.lastowner", current, Role.CONTRIBUTOR, adminUser));
    } finally {
      client.deleteGroup("test.role.lastowner");
    }
  }

  @Test
  public void changeRole_promote_succeeds() {
    Group g = new Group("test.role.promote", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000001");
    UUID target = UUID.fromString("33000001-0000-0000-0000-000000000002");
    try {
      client.insertMember(new Member("test.role.promote", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.role.promote", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.changeRole("test.role.promote", target, Role.OWNER, new User(current, "a@x", "A"));
      Optional<Member> after = client.findMember("test.role.promote", target);
      assertEquals(after.get().role(), Role.OWNER);
    } finally {
      client.deleteGroup("test.role.promote");
    }
  }

  @Test
  public void changeRole_self_throws() {
    Group g = new Group("test.role.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID current = UUID.fromString("33000001-0000-0000-0000-000000000006");
    try {
      client.insertMember(new Member("test.role.self", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(current, "a@x", "A");
      expectThrows(ValidationException.class,
          () -> service.changeRole("test.role.self", current, Role.CONTRIBUTOR, currentUser));
    } finally {
      client.deleteGroup("test.role.self");
    }
  }

  @Test
  public void decline_Invitation_active_isNoOp() {
    Group g = new Group("test.decline.active", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000002");
    try {
      client.insertMember(new Member("test.decline.active", userId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      service.declineInvitation("test.decline.active", userId);
      assertTrue(client.findMember("test.decline.active", userId).isPresent());
    } finally {
      client.deleteGroup("test.decline.active");
    }
  }

  @Test
  public void decline_Invitation_pending_deletesRow() {
    Group g = new Group("test.decline.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID userId = UUID.fromString("11000001-0000-0000-0000-000000000001");
    try {
      client.insertMember(new Member("test.decline.fixture", userId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.ofEpochMilli(1L), null));
      service.declineInvitation("test.decline.fixture", userId);
      assertTrue(client.findMember("test.decline.fixture", userId).isEmpty());
    } finally {
      client.deleteGroup("test.decline.fixture");
    }
  }

  @Test
  public void invite_alreadyMember_throwsValidation() {
    Group g = new Group("test.invite.dup", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
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
      client.deleteGroup("test.invite.dup");
    }
  }

  @Test
  public void invite_blankEmail_throwsValidation() {
    Group g = new Group("test.invite.blank", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID inviterId = UUID.fromString("ee000001-0000-0000-0000-000000000002");
    User inviter = new User(inviterId, "inviter@lattejava.org", "Inviter");
    try {
      ValidationException ex = expectThrows(
          ValidationException.class,
          () -> service.invite(new InviteRequest("test.invite.blank", "  ", Role.CONTRIBUTOR), inviter)
      );
      assertNotNull(ex.errors().getFieldError("email", "[blank]email"));
    } finally {
      client.deleteGroup("test.invite.blank");
    }
  }

  @Test
  public void invite_newEmail_createsFAUserAndPendingMember() {
    Group g = new Group("test.invite.new", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
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

      Optional<Member> persisted = client.findMember("test.invite.new", m.userId());
      assertTrue(persisted.isPresent());
    } finally {
      client.deleteGroup("test.invite.new");
    }
  }

  @Test
  public void leave_contributor_succeeds() {
    Group g = new Group("test.leave.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000001");
    UUID leaver = UUID.fromString("44000001-0000-0000-0000-000000000002");
    try {
      client.insertMember(new Member("test.leave.contrib", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.leave.contrib", leaver, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.leave("test.leave.contrib", new User(leaver, "l@x", "L"));
      assertTrue(client.findMember("test.leave.contrib", leaver).isEmpty());
    } finally {
      client.deleteGroup("test.leave.contrib");
    }
  }

  @Test
  public void leave_lastActiveOwner_throws() {
    Group g = new Group("test.leave.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID owner = UUID.fromString("44000001-0000-0000-0000-000000000003");
    try {
      client.insertMember(new Member("test.leave.lastowner", owner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User ownerUser = new User(owner, "o@x", "O");
      expectThrows(ValidationException.class, () -> service.leave("test.leave.lastowner", ownerUser));
    } finally {
      client.deleteGroup("test.leave.lastowner");
    }
  }

  @Test
  public void remove_contributor_succeeds() {
    Group g = new Group("test.remove.contrib", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID current = UUID.fromString("22000001-0000-0000-0000-000000000001");
    UUID target = UUID.fromString("22000001-0000-0000-0000-000000000002");
    try {
      client.insertMember(new Member("test.remove.contrib", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      client.insertMember(new Member("test.remove.contrib", target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(2L)));
      service.remove("test.remove.contrib", target, new User(current, "actor@lattejava.org", "Actor"));
      assertTrue(client.findMember("test.remove.contrib", target).isEmpty());
    } finally {
      client.deleteGroup("test.remove.contrib");
    }
  }

  @Test
  public void remove_self_throws() {
    Group g = new Group("test.remove.self", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID current = UUID.fromString("22000001-0000-0000-0000-000000000005");
    try {
      client.insertMember(new Member("test.remove.self", current, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(current, "actor@lattejava.org", "Actor");
      expectThrows(ValidationException.class, () -> service.remove("test.remove.self", current, currentUser));
    } finally {
      client.deleteGroup("test.remove.self");
    }
  }

  @Test
  public void remove_wouldOrphanGroup_isPrevented() {
    Group g = new Group("test.remove.lastowner", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1L), Instant.ofEpochMilli(1L));
    client.insertGroup(g);
    UUID lastOwner = UUID.fromString("22000001-0000-0000-0000-000000000006");
    UUID adminActor = UUID.fromString("22000001-0000-0000-0000-000000000007");
    try {
      client.insertMember(new Member("test.remove.lastowner", lastOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.ofEpochMilli(1L)));
      User currentUser = new User(adminActor, "actor@lattejava.org", "Actor");
      expectThrows(ValidationException.class, () -> service.remove("test.remove.lastowner", lastOwner, currentUser));
    } finally {
      client.deleteGroup("test.remove.lastowner");
    }
  }
}
