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
import org.lattejava.app.model.Member;

/**
 * Wiring matrix for every protected path under {@code /app/groups}: for each owner-only path, asserts a 303 redirect
 * to {@code /app/} against a missing group, a non-member, and an active CONTRIBUTOR; for each active-member-only path
 * (any role), asserts a 303 against missing/non-member/PENDING and a 200 for an active CONTRIBUTOR; for each
 * member-only path, asserts the same redirect against a missing group and a non-member. This verifies that the correct
 * GroupSecurity middleware is actually attached to each specific route, complementing the middleware-logic tests in
 * {@link GroupSecurityTest}. All denial paths collapse into the same redirect to avoid leaking whether the resource
 * exists or whether the user just lacks a role.
 * <p>
 * Unguarded routes (GET /, GET /new, POST /new, POST .../accept, POST .../decline) are intentionally absent.
 */
public class GroupSecurityWiringTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";
  private static final String CONTRIBUTOR_GROUP = "test.security.wiring.contrib";
  private static final String MISSING_GROUP = "test.security.wiring.missing";
  private static final String NON_MEMBER_GROUP = "test.security.wiring.nonmember";
  private static final String PENDING_GROUP = "test.security.wiring.pending";
  private static final UUID TARGET = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");

  @DataProvider(name = "activeMemberOnly")
  public Object[][] activeMemberOnly() {
    return new Object[][]{
        {"GET", "/settings"},
    };
  }

  @Test(dataProvider = "activeMemberOnly")
  public void activeMemberOnly_activeContributor_passes(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + CONTRIBUTOR_GROUP + suffix).assertStatus(200);
  }

  @Test(dataProvider = "activeMemberOnly")
  public void activeMemberOnly_missingGroup_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + MISSING_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @Test(dataProvider = "activeMemberOnly")
  public void activeMemberOnly_nonMember_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + NON_MEMBER_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @Test(dataProvider = "activeMemberOnly")
  public void activeMemberOnly_pendingMember_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + PENDING_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @DataProvider(name = "memberOnly")
  public Object[][] memberOnly() {
    return new Object[][]{
        {"GET", "/"},
        {"GET", "/members/leave"},
        {"POST", "/members/leave"},
    };
  }

  @Test(dataProvider = "memberOnly")
  public void memberOnly_missingGroup_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + MISSING_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @Test(dataProvider = "memberOnly")
  public void memberOnly_nonMember_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + NON_MEMBER_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @DataProvider(name = "ownerOnly")
  public Object[][] ownerOnly() {
    return new Object[][]{
        {"POST", "/settings"},
        {"GET", "/verify"},
        {"GET", "/delete"},
        {"POST", "/delete"},
        {"POST", "/verify/check"},
        {"POST", "/verify/github"},
        {"GET", "/members/"},
        {"GET", "/members/invite"},
        {"POST", "/members/invite"},
        {"GET", "/members/" + TARGET + "/remove"},
        {"POST", "/members/" + TARGET + "/remove"},
        {"GET", "/members/" + TARGET + "/role"},
        {"POST", "/members/" + TARGET + "/role"},
    };
  }

  @Test(dataProvider = "ownerOnly")
  public void ownerOnly_contributor_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + CONTRIBUTOR_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @Test(dataProvider = "ownerOnly")
  public void ownerOnly_missingGroup_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + MISSING_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @Test(dataProvider = "ownerOnly")
  public void ownerOnly_nonMember_redirectsHome(String method, String suffix) throws Exception {
    oidc.login("test@lattejava.org", "password", APP_ID);
    request(method, "/app/groups/" + NON_MEMBER_GROUP + suffix).assertRedirect(303, "/app/");
  }

  @AfterClass
  public void wiringAfterClass() {
    db.deleteGroup(NON_MEMBER_GROUP);
    db.deleteGroup(CONTRIBUTOR_GROUP);
    db.deleteGroup(PENDING_GROUP);
  }

  @BeforeClass
  public void wiringBeforeClass() {
    db.insertGroup(new Group(NON_MEMBER_GROUP, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertGroup(new Group(CONTRIBUTOR_GROUP, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertGroup(new Group(PENDING_GROUP, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(CONTRIBUTOR_GROUP, testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    db.insertMember(new Member(PENDING_GROUP, testUserId, Role.OWNER, MembershipState.PENDING, null, Instant.now(), null));
  }

  private WebTestAsserter request(String method, String path) {
    return switch (method) {
      case "GET" -> test.get(path);
      case "POST" -> test.post(path);
      default -> throw new IllegalStateException("Unsupported method [" + method + "]");
    };
  }
}
