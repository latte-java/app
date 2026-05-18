/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.github;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.Configuration;
import org.testng.SkipException;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Live-API test for {@link GitHubHTTPClient}. Skipped unless {@code github.token} is configured in
 * {@code ~/.config/latte/app/config.properties}. The token must be a GitHub personal access token with at
 * least the {@code read:user}, {@code user:email}, and {@code read:org} scopes, owned by user
 * {@value #USER}, who is a member of the {@value #ORG} organization.
 */
@Test
public class GitHubHTTPClientTest {
  private static final String ORG = "latte-java";
  private static final String USER = "voidmain";
  public GitHubHTTPClient client;
  public String token;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of(),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    token = config.get("github.token");
    if (token == null || token.isBlank()) {
      throw new SkipException("github.token not configured in ~/.config/latte/app/config.properties; skipping live GitHub tests");
    }
    client = new GitHubHTTPClient();
  }

  @Test
  public void checkOrgMembership_memberOfOrg_returnsMember() {
    MembershipStatus status = client.checkOrgMembership(token, ORG, USER);
    assertEquals(status, MembershipStatus.MEMBER,
        "Expected [" + USER + "] to be a visible member of [" + ORG + "]. "
            + "Got [" + status + "]. If NOT_MEMBER, the token likely lacks read:org scope (classic) or "
            + "Members:Read permission (fine-grained) — GitHub returns 302 from /orgs/{org}/members/{user} "
            + "when the caller isn't recognized as an org member, which our client maps to NOT_MEMBER.");
  }

  @Test
  public void checkOrgMembership_nonexistentUser_returnsNotMember() {
    String bogus = "lj-test-" + UUID.randomUUID().toString().replace("-", "");
    assertEquals(client.checkOrgMembership(token, ORG, bogus), MembershipStatus.NOT_MEMBER);
  }

  @Test
  public void getAccountType_nonexistentAccount_returnsNotFound() {
    String bogus = "lj-test-" + UUID.randomUUID().toString().replace("-", "");
    assertEquals(client.getAccountType(token, bogus), AccountType.NOT_FOUND);
  }

  @Test
  public void getAccountType_organization_returnsOrganization() {
    assertEquals(client.getAccountType(token, ORG), AccountType.ORGANIZATION);
  }

  @Test
  public void getAccountType_user_returnsUser() {
    assertEquals(client.getAccountType(token, USER), AccountType.USER);
  }

  @Test
  public void getLogin_returnsAuthenticatedLogin() {
    assertEquals(client.getLogin(token), USER);
  }
}
