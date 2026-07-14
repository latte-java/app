/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.fusionauth;
import module org.lattejava.web;
import module org.testng;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.lattejava.app.Main;
import org.lattejava.app.db.DatabaseService;
import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;

public abstract class BaseTest {
  public static DatabaseService db;
  public static Main main;
  public static OIDCTestFixture oidc;
  public static OIDCTestFixture oidcForAPI;
  public static WebTest test = new WebTest(8081);
  public static UUID testUserId;

  @AfterSuite
  public static void afterSuite() {
    main.close();
  }

  @BeforeSuite
  public static void beforeSuite() throws Exception {
    main = new Main(8081, true);
    main.main();
    db = Services.databaseService();
    oidc = new OIDCTestFixture(test, main.ssrConfig);
    oidcForAPI = new OIDCTestFixture(test, main.apiConfig);

    // Discover the FA test user UUID once; the per-method reset re-seeds the OWNER membership with it.
    FusionAuthClient fa = new FusionAuthClient(
        main.config.get("fusionauth.apiKey"),
        main.config.get("fusionauth.baseUrl")
    );
    var userResponse = fa.retrieveUser(null, null, null, null, "test@lattejava.org", null, null);
    if (userResponse == null || userResponse.user() == null) {
      throw new IllegalStateException("FA test user not found - is FusionAuth running with kickstart applied?");
    }
    testUserId = userResponse.user().id();
  }

  /**
   * Inserts the FA test user as an ACTIVE OWNER of the given group. Use this in flow tests to satisfy the
   * {@link org.lattejava.app.security.GroupSecurity} middleware on owner-only routes (settings, verify, delete, invite,
   * role/remove).
   */
  public static void insertTestUserAsOwner(String groupName) {
    db.insertMember(new Member(groupName, testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
  }

  private static void resetAndSeedDatabase() {
    // Wipe all rows in dependency order (child tables first), via a raw connection — DatabaseService
    // intentionally exposes no bulk-delete on its production API.
    try (Connection c = DriverManager.getConnection(
             main.config.get("db.url"), main.config.get("db.username"), main.config.get("db.password"));
         Statement s = c.createStatement()) {
      s.execute("DELETE FROM members");
      s.execute("DELETE FROM group_verifications");
      s.execute("DELETE FROM groups");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to reset the test database", e);
    }

    // Re-seed the reserved group and the FA test user as its OWNER.
    Instant seedTime = Instant.ofEpochMilli(1714867200000L);
    db.insertGroup(new Group("org.lattejava", "Reserved group for the Latte Java project.", GroupState.VERIFIED, null, seedTime, seedTime));
    db.insertMember(new Member("org.lattejava", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, seedTime));
  }

  @AfterMethod
  public void afterMethod() {
    oidc.logout();
  }

  @BeforeMethod
  public void beforeMethod() {
    resetAndSeedDatabase();
  }
}
