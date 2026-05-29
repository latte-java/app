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
 * HTTP-level coverage of the dedicated change-role page: the form renders with the member's current role pre-selected
 * on the radio-card, missing group/member return 404, POST updates the role and redirects to the members listing with a
 * trailing slash, and the members listing renders the "Change role" link in place of the old inline select form.
 */
@Test
public class RoleFlowTest extends BaseTest {
  @Test
  public void changeRole_demoteOwnerWithOtherOwners_updatesDb() throws Exception {
    String name = "test.role.demote";
    UUID target = UUID.fromString("aa000001-0000-0000-0000-000000000001");
    UUID otherOwner = UUID.fromString("aa000001-0000-0000-0000-000000000002");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      db.insertMember(new Member(name, target, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      db.insertMember(new Member(name, otherOwner, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      oidc.login("test@lattejava.org", "password");
      test.withForm(Map.of("role", "CONTRIBUTOR"))
          .post("/app/groups/" + name + "/members/" + target + "/role")
          .assertRedirect(303, "/app/groups/" + name + "/members/");

      Optional<Member> after = db.findMember(name, target);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.CONTRIBUTOR);
    } finally {
      db.deleteGroup(name);
    }
  }

  // changeRole_demoteLastOwner_rerendersRoleFormWithError removed: the scenario is unreachable through HTTP under
  // GroupSecurity — the actor must be an OWNER, the validator's last-owner check requires the target to be the only
  // active OWNER, and the validator's self-rule check forbids actor == target. Validator coverage for that case lives
  // in the service-level MembershipServiceTest.

  @Test
  public void changeRole_promoteContributor_redirectsWithTrailingSlashAndUpdatesDb() throws Exception {
    String name = "test.role.promote";
    UUID target = UUID.fromString("bb000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      db.insertMember(new Member(name, target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      oidc.login("test@lattejava.org", "password");
      test.withForm(Map.of("role", "OWNER"))
          .post("/app/groups/" + name + "/members/" + target + "/role")
          .assertRedirect(303, "/app/groups/" + name + "/members/");

      Optional<Member> after = db.findMember(name, target);
      assertTrue(after.isPresent());
      assertEquals(after.get().role(), Role.OWNER);
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void membersList_hidesChangeRoleAndRemoveOnSelfRow() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password");
    UUID testUserId = db.listMembers("org.lattejava").getFirst().userId();
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.doesNotContain("href=\"/app/groups/org.lattejava/members/" + testUserId + "/role\"")
                                    .doesNotContain("/app/groups/org.lattejava/members/" + testUserId + "/remove")
                                    .doesNotContain("<option value=\"OWNER\""));
  }

  @Test
  public void membersList_showsChangeRoleAndRemoveForOtherMembers() throws Exception {
    String name = "test.role.others";
    UUID other = UUID.fromString("ab000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    try {
      UUID testUserId = db.listMembers("org.lattejava").getFirst().userId();
      db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      db.insertMember(new Member(name, other, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/members/" + other + "/role\"")
                                      .contains("/app/groups/" + name + "/members/" + other + "/remove")
                                      .doesNotContain("href=\"/app/groups/" + name + "/members/" + testUserId + "/role\"")
                                      .doesNotContain("/app/groups/" + name + "/members/" + testUserId + "/remove"));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void roleForm_currentRoleContributor_rendersFormWithContributorChecked() throws Exception {
    String name = "test.role.form.contrib";
    UUID target = UUID.fromString("cc000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      db.insertMember(new Member(name, target, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/" + target + "/role")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Change role")
                                      .contains("action=\"/app/groups/" + name + "/members/" + target + "/role\"")
                                      .contains("value=\"CONTRIBUTOR\" checked")
                                      .contains("value=\"OWNER\"")
                                      .doesNotContain("value=\"OWNER\" checked"));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void roleForm_currentRoleOwner_rendersFormWithOwnerChecked() throws Exception {
    String name = "test.role.form.owner";
    UUID target = UUID.fromString("dd000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      db.insertMember(new Member(name, target, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/" + target + "/role")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Change role")
                                      .contains("value=\"OWNER\" checked")
                                      .contains("value=\"CONTRIBUTOR\"")
                                      .doesNotContain("value=\"CONTRIBUTOR\" checked"));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void roleForm_missingGroup_redirectsHome() throws Exception {
    UUID target = UUID.fromString("ee000001-0000-0000-0000-000000000001");
    oidc.login("test@lattejava.org", "password");
    test.get("/app/groups/test.role.missing/members/" + target + "/role")
        .assertRedirect(303, "/app/");
  }

  @Test
  public void roleForm_missingMember_returns404() throws Exception {
    String name = "test.role.nomember";
    UUID target = UUID.fromString("ff000001-0000-0000-0000-000000000001");
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      oidc.login("test@lattejava.org", "password");
      test.get("/app/groups/" + name + "/members/" + target + "/role")
          .assertStatus(404);
    } finally {
      db.deleteGroup(name);
    }
  }
}
