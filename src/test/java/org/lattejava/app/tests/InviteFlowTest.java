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

import static org.testng.Assert.*;

/**
 * HTTP-level coverage of the dedicated invite-member page: the form renders with both role cards, a missing group 404s,
 * a blank email re-renders the invite page with the field error, a valid email creates a pending member and redirects
 * to the members list, and the members list links to the new page.
 */
@Test
public class InviteFlowTest extends BaseTest {
  @Test
  public void inviteForm_missingGroup_redirectsHome() throws Exception {
    oidc.login("test@lattejava.org", "password");
    test.get("/app/groups/test.invite.missing/members/invite")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void inviteForm_rendersFormAndRoleCards() throws Exception {
    String name = "test.invite.form";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/invite")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Invite a member")
                                      .contains("action=\"/app/groups/" + name + "/members/invite\"")
                                      .contains("name=\"email\"")
                                      .contains("name=\"role\"")
                                      .contains("value=\"CONTRIBUTOR\"")
                                      .contains("value=\"OWNER\"")
                                      .contains("Contributor")
                                      .contains("Owner"));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void invite_blankEmail_rerendersInviteForm() throws Exception {
    String name = "test.invite.blank";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password");
      test.withForm(Map.of("email", "", "role", "CONTRIBUTOR"))
          .post("/app/groups/" + name + "/members/invite")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("An email address is required.")
                                      .contains("action=\"/app/groups/" + name + "/members/invite\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void invite_validEmail_redirectsToMembersAndCreatesPendingMember() throws Exception {
    String name = "test.invite.create";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    String email = "test+invite-page-" + UUID.randomUUID() + "@lattejava.org";
    try {
      oidc.login("test@lattejava.org", "password");
      test.withForm(Map.of("email", email, "role", "CONTRIBUTOR"))
          .post("/app/groups/" + name + "/members/invite")
          .assertRedirect(303, "/app/groups/" + name + "/members/");

      var pending = db.listMembers(name).stream()
                      .filter(m -> m.state() == MembershipState.PENDING)
                      .toList();
      assertEquals(pending.size(), 1);
      assertEquals(pending.getFirst().role(), Role.CONTRIBUTOR);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void membersList_inviteButtonLinksToInvitePage() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password");
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("href=\"/app/groups/org.lattejava/members/invite\"")
                                    .doesNotContain("?invite=1#invite"));
  }
}
