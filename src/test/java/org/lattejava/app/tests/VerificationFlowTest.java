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

import java.util.Optional;

import static org.testng.Assert.*;

/**
 * HTTP-level coverage of the group verification flows: a pending reverse-DNS group renders the TXT instructions, a
 * failed group renders the expired notice, the check endpoint redirects back to the group detail page, and an
 * already-verified group is bounced away from both the verify form and the check endpoint without being re-verified.
 */
@Test
public class VerificationFlowTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  @Test
  public void checkVerification_pendingGitHubGroup_redirectsToDetail() throws Exception {
    String name = "io.github.verifyflow.check";
    db.insertGroup(new Group(name, "", GroupState.PENDING, null, Instant.now(), null));
    insertTestUserAsOwner(name);
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/verify/check")
          .assertRedirect(303, "/app/groups/" + name + "/");

      Optional<Group> after = db.findGroup(name);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.PENDING);
      assertFalse(db.findVerification(name).isPresent());
    } finally {
      db.deleteVerification(name);
      db.deleteGroup(name);
    }
  }

  @Test
  public void checkVerification_verifiedGroup_redirectsAndStaysVerified() throws Exception {
    String name = "test.verifyflow.checkverified";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.post("/app/groups/" + name + "/verify/check")
          .assertRedirect(303, "/app/groups/" + name + "/");

      Optional<Group> after = db.findGroup(name);
      assertTrue(after.isPresent());
      assertEquals(after.get().state(), GroupState.VERIFIED);
      assertFalse(db.findVerification(name).isPresent());
    } finally {
      db.deleteVerification(name);
      db.deleteGroup(name);
    }
  }

  @Test
  public void verifyForm_failedGroup_rendersExpiredNotice() throws Exception {
    String name = "test.verifyflow.failed";
    db.insertGroup(new Group(name, "", GroupState.FAILED, "code-failed", Instant.now(), null));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/verify")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("previous verification attempt expired"));
    } finally {
      db.deleteVerification(name);
      db.deleteGroup(name);
    }
  }

  @Test
  public void verifyForm_pendingReverseDNSGroup_rendersTXTInstructions() throws Exception {
    String name = "test.verifyflow.pending";
    db.insertGroup(new Group(name, "", GroupState.PENDING, "code-pending", Instant.now(), null));
    db.insertVerification(new GroupVerification(name, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/verify")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Add this TXT record").contains("pending.verifyflow.test"));
    } finally {
      db.deleteVerification(name);
      db.deleteGroup(name);
    }
  }

  @Test
  public void verifyForm_verifiedGroup_redirectsToDetail() throws Exception {
    String name = "test.verifyflow.verified";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/verify")
          .assertRedirect(303, "/app/groups/" + name + "/");
    } finally {
      db.deleteVerification(name);
      db.deleteGroup(name);
    }
  }
}
