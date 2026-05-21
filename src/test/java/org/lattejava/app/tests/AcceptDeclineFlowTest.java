/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.testng;
import java.util.Optional;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;

import static org.testng.Assert.*;

/**
 * HTTP-level coverage of the invitation accept and decline flows. Accept and decline always operate on the
 * authenticated user — the URL carries no userId, so there is no way to act on another member's row. A PENDING
 * member POSTing accept becomes ACTIVE with joinedAt set and is redirected to the group detail; an already-ACTIVE
 * member POSTing accept is a no-op (joinedAt unchanged). A PENDING member POSTing decline has their row deleted
 * and is redirected to the groups listing; an already-ACTIVE member POSTing decline is a no-op (row remains).
 * A caller with no membership row is redirected home by GroupSecurity before the controller runs.
 */
@Test
public class AcceptDeclineFlowTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  @Test
  public void accept_activeMember_redirectsAndJoinedAtUnchanged() throws Exception {
    String name = "test.accept.active";
    Instant originalJoinedAt = Instant.ofEpochMilli(1714867200000L);
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, originalJoinedAt));
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/accept")
          .assertRedirect(303, "/app/groups/" + name + "/");

      Optional<Member> after = db.findMember(name, testUserId);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertEquals(after.get().joinedAt(), originalJoinedAt);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void accept_afterOwnerCancelsInvitation_doesNotGrantAccess() throws Exception {
    // Reported bug: invited user sits on the group page; owner cancels the invitation; user clicks Accept
    // and (per the report) "still has access to the group". With the row deleted, GroupSecurity must reject
    // the POST before the controller runs, leaving the caller un-joined.
    String name = "test.cancel.then.accept";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.now(), null));
      // Owner cancels — simulated as a direct row delete.
      db.deleteMember(name, testUserId);
      assertTrue(db.findMember(name, testUserId).isEmpty(), "precondition: pending row removed");

      oidc.login("test@lattejava.org", "password", APP_ID);
      // Stale Accept button POSTs; GroupSecurity has no row to match and redirects home.
      test.post("/app/groups/" + name + "/members/accept")
          .assertRedirect(303, "/app/");

      assertTrue(db.findMember(name, testUserId).isEmpty(),
          "After a cancellation, a later accept must NOT grant access");
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void accept_pendingMember_marksActiveAndRedirects() throws Exception {
    String name = "test.accept.pending";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.now(), null));
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/accept")
          .assertRedirect(303, "/app/groups/" + name + "/");

      Optional<Member> after = db.findMember(name, testUserId);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.CONTRIBUTOR);
      assertEquals(after.get().state(), MembershipState.ACTIVE);
      assertNotNull(after.get().joinedAt());
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void accept_callerNotMember_redirectsHomeAndIsNoOp() throws Exception {
    // Authenticated user has no membership row in the group — GroupSecurity sends them home before the controller
    // runs. No member row is created.
    String name = "test.accept.unknown";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/accept")
          .assertRedirect(303, "/app/");

      assertTrue(db.findMember(name, testUserId).isEmpty());
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void decline_activeMember_redirectsAndRowRemains() throws Exception {
    String name = "test.decline.active";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/decline")
          .assertRedirect(303, "/app/groups/");

      Optional<Member> after = db.findMember(name, testUserId);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.OWNER);
      assertEquals(after.get().state(), MembershipState.ACTIVE);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void decline_pendingMember_deletesRowAndRedirects() throws Exception {
    String name = "test.decline.pending";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.now(), null));
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/decline")
          .assertRedirect(303, "/app/groups/");

      assertTrue(db.findMember(name, testUserId).isEmpty());
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void decline_callerNotMember_redirectsHomeAndIsNoOp() throws Exception {
    // Authenticated user has no membership row in the group — GroupSecurity sends them home before the controller
    // runs. No row is touched.
    String name = "test.decline.unknown";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/decline")
          .assertRedirect(303, "/app/");

      assertTrue(db.findMember(name, testUserId).isEmpty());
    } finally {
      db.deleteGroup(name);
    }
  }
}
