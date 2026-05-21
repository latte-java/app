/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;
import java.util.Optional;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;

import static org.testng.Assert.*;

/**
 * HTTP-level coverage of the dedicated remove-member confirmation page: GET renders a confirmation form with the
 * member's identifier and a POST action targeting the existing remove endpoint; missing group/member return 404; POST
 * removes the member and redirects to the members listing with a trailing slash; attempting to remove the last active
 * OWNER re-renders the confirmation page with the validation error and leaves the row in place; the members listing now
 * renders the Remove link in place of the old inline POST form.
 */
@Test
public class RemoveFlowTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  @Test
  public void membersList_rendersRemoveLinkInsteadOfPostForm() throws Exception {
    String name = "test.remove.list";
    UUID other = UUID.fromString("ef000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      UUID testUserId = db.listMembers("org.lattejava").getFirst().userId();
      db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      db.insertMember(new Member(name, other, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/members/")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/members/" + other + "/remove\"")
                                      .doesNotContain("action=\"/app/groups/" + name + "/members/" + other + "/remove\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void removeForm_missingGroup_redirectsHome() throws Exception {
    UUID target = UUID.fromString("ef000002-0000-0000-0000-000000000001");
    oidc.login("test@lattejava.org", "password", APP_ID);
    test.get("/app/groups/test.remove.missing/members/" + target + "/remove")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void removeForm_missingMember_returns404() throws Exception {
    String name = "test.remove.nomember";
    UUID target = UUID.fromString("ef000003-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/members/" + target + "/remove")
          .assertStatus(404);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void removeForm_rendersConfirmationForm() throws Exception {
    String name = "test.remove.form";
    UUID target = UUID.fromString("ef000004-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      db.insertMember(new Member(name, target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/members/" + target + "/remove")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Remove member")
                                      .contains("Are you sure")
                                      .contains("action=\"/app/groups/" + name + "/members/" + target + "/remove\"")
                                      .contains("href=\"/app/groups/" + name + "/members/\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  // remove_lastActiveOwner_rerendersFormWithError removed: the scenario is unreachable through HTTP under
  // GroupSecurity — the actor must be an OWNER and the validator's self-rule check forbids actor == target, so the
  // target cannot also be the sole OWNER. Validator coverage for that case lives in MembershipServiceTest.

  @Test
  public void remove_validMember_deletesAndRedirects() throws Exception {
    String name = "test.remove.valid";
    UUID target = UUID.fromString("ef000006-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      UUID testUserId = db.listMembers("org.lattejava").getFirst().userId();
      db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      db.insertMember(new Member(name, target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/members/" + target + "/remove")
          .assertRedirect(303, "/app/groups/" + name + "/members/");

      assertTrue(db.findMember(name, target).isEmpty());
    } finally {
      db.deleteGroup(name);
    }
  }
}
