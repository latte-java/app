/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;
import module restify;

import org.lattejava.app.Main;

@Test
public abstract class BaseTest {
  public static DatabaseClient db;
  public static Main main;
  public static OIDCTestFixture oidc;
  public static WebTest test = new WebTest(Main.PORT);

  @AfterMethod
  public void afterMethod() {
    oidc.logout();
  }

  @AfterSuite
  public static void afterSuite() {
    main.close();
  }

  @BeforeSuite
  public static void beforeSuite() {
    main = new Main();
    main.main();
    db = new DatabaseClient(main.config);
    oidc = new OIDCTestFixture(test, main.oidcConfig);

    resetAndSeedDatabase();
  }

  private static void resetAndSeedDatabase() {
    DatabaseClient db = new DatabaseClient(main.config);
    // Explicit deletes in dependency order. Not relying on FK CASCADE so the
    // order is visible and intentional.
    db.query("DELETE FROM group_verifications");
    db.query("DELETE FROM members");
    db.query("DELETE FROM groups");

    // Re-insert the reserved group (the migration would have, but DELETE wiped it).
    db.query(
        "INSERT INTO groups (name, description, state, verification_code, created_at, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
        "org.lattejava",
        "Reserved group for the Latte Java project.",
        "VERIFIED",
        null,
        1714867200000L,
        1714867200000L
    );

    // Discover the FA test user UUID and seed an OWNER membership.
    FusionAuthClient fa = new FusionAuthClient(
        main.config.get("fusionauth.apiKey"),
        main.config.get("fusionauth.baseUrl")
    );
    ClientResponse<UserResponse, ?> userResponse = fa.retrieveUserByEmail("test@lattejava.org");
    if (!userResponse.wasSuccessful() || userResponse.successResponse.user == null) {
      throw new IllegalStateException("FA test user not found - is FusionAuth running with kickstart applied?");
    }
    UUID testUserId = userResponse.successResponse.user.id;
    db.query(
        "INSERT INTO members (group_name, user_id, role, state, invited_by, invited_at, joined_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        "org.lattejava",
        testUserId.toString(),
        "OWNER",
        "ACTIVE",
        null,
        null,
        1714867200000L
    );
  }
}
