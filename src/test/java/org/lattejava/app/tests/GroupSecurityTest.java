/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.testng;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;

/**
 * HTTP-level coverage of the {@link org.lattejava.app.security.GroupSecurity} middleware (installed at
 * {@code /app/groups}) and the {@code hasRole(OWNER)} sibling. Membership cases (use the {@code GET /{groupName}/}
 * detail route, which is gated only by the base membership check): active member (200), pending member (200 — invitees
 * can view the group), missing group / non-member (303 → {@code /app/}). Role cases (use the owner-only
 * {@code /members/invite} route): missing group / non-member / pending member / active CONTRIBUTOR (303 →
 * {@code /app/}). Owner success is covered indirectly by every passing test that uses
 * {@link BaseTest#insertTestUserAsOwner(String)}.
 */
@Test
public class GroupSecurityTest extends BaseTest {
  @Test
  public void hasRole_contributor_redirectsHome() throws Exception {
    String name = "test.security.contrib";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/invite")
          .assertRedirect(303, "/app/");
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void hasRole_missingGroup_redirectsHome() throws Exception {
    oidc.login("test@lattejava.org", "password");
    test.get("/app/groups/test.security.nogroup/members/invite")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void hasRole_nonMember_redirectsHome() throws Exception {
    String name = "test.security.nonmember";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/invite")
          .assertRedirect(303, "/app/");
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void hasRole_pendingMember_redirectsHome() throws Exception {
    String name = "test.security.pending";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.PENDING, null, Instant.now(), null));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/invite")
          .assertRedirect(303, "/app/");
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void isMember_activeMember_passes() throws Exception {
    String name = "test.security.ismember";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/")
          .assertStatus(200);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void isMember_missingGroup_redirectsHome() throws Exception {
    oidc.login("test@lattejava.org", "password");
    test.get("/app/groups/test.security.ismember.nogroup/")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void isMember_nonMember_redirectsHome() throws Exception {
    String name = "test.security.ismember.nonmember";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/")
          .assertRedirect(303, "/app/");
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void isMember_pendingMember_passes() throws Exception {
    // PENDING invitees can view group routes so they can find Accept/Decline. The stricter HasRole gate still
    // rejects PENDING actors on owner-only routes (covered by hasRole_pendingMember_redirectsHome).
    String name = "test.security.ismember.pending";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.PENDING, null, Instant.now(), null));
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/")
          .assertStatus(200);
    } finally {
      db.deleteGroup(name);
    }
  }
}
