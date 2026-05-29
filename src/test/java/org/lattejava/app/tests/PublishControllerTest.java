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
import org.lattejava.web.oidc.Tokens;

@Test
public class PublishControllerTest extends BaseTest {
  private static Group verifiedGroup(String name) {
    return new Group(name, "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
  }

  private WebTestAsserter publish(String groupName, String body) throws Exception {
    Tokens tokens = oidcForAPI.login("test@lattejava.org", "password", "http://localhost:8888/callback");
    test.clearRequestState();
    test.clearCookies();
    return test.withHeader("Authorization", "Bearer " + tokens.accessToken())
               .withHeader("Content-Type", "application/json")
               .withBody(body)
               .post("/api/v1/publish/" + groupName);
  }

  // The shared WebTest is static across the whole suite; these tests set a JSON Content-Type header, which would
  // otherwise leak into later test classes and trip the withForm Content-Type guard. Clear request state after each.
  @AfterMethod
  public void clearPublishRequestState() {
    test.clearRequestState();
  }

  @Test
  public void publish_ownedVerifiedGroup_returnsPresignedURL() throws Exception {
    var string = new StringBodyAsserter();
    publish("org.lattejava", "{\"fileName\":\"org/lattejava/1.0.0/lib-1.0.0.jar\"}")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("org/lattejava/1.0.0/lib-1.0.0.jar").contains("X-Amz-Signature"));
  }

  @Test
  public void publish_activeContributor_returnsPresignedURL() throws Exception {
    var string = new StringBodyAsserter();
    db.insertGroup(verifiedGroup("com.contribtest"));
    db.insertMember(new Member("com.contribtest", testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      publish("com.contribtest", "{\"fileName\":\"com/contribtest/x.jar\"}")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("com/contribtest/x.jar").contains("X-Amz-Signature"));
    } finally {
      db.deleteMember("com.contribtest", testUserId);
      db.deleteGroup("com.contribtest");
    }
  }

  @Test
  public void publish_pendingMembership_returns403() throws Exception {
    db.insertGroup(verifiedGroup("com.pendingmember"));
    db.insertMember(new Member("com.pendingmember", testUserId, Role.OWNER, MembershipState.PENDING, null, null, null));
    try {
      publish("com.pendingmember", "{\"fileName\":\"com/pendingmember/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteMember("com.pendingmember", testUserId);
      db.deleteGroup("com.pendingmember");
    }
  }

  @Test
  public void publish_noOwningGroup_returns403() throws Exception {
    publish("net.unregistered.thing", "{\"fileName\":\"net/unregistered/thing/x.jar\"}")
        .assertStatus(403);
  }

  @Test
  public void publish_notAMember_returns403() throws Exception {
    db.insertGroup(verifiedGroup("com.notmine"));
    try {
      publish("com.notmine", "{\"fileName\":\"com/notmine/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteGroup("com.notmine");
    }
  }

  @Test
  public void publish_unverifiedGroup_returns403() throws Exception {
    db.insertGroup(new Group("com.pendingpub", "", GroupState.PENDING, "code", Instant.ofEpochMilli(1714867200000L), null));
    db.insertMember(new Member("com.pendingpub", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      publish("com.pendingpub", "{\"fileName\":\"com/pendingpub/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteMember("com.pendingpub", testUserId);
      db.deleteGroup("com.pendingpub");
    }
  }

  @Test
  public void publish_nestedGroupNotMember_returns403() throws Exception {
    db.insertGroup(verifiedGroup("com.parentpub"));
    db.insertMember(new Member("com.parentpub", testUserId, Role.OWNER, MembershipState.ACTIVE, null, null, Instant.now()));
    db.insertGroup(verifiedGroup("com.parentpub.child"));
    try {
      // Owner of com.parentpub, but com.parentpub.child is a separate group they do not belong to.
      publish("com.parentpub.child.artifact", "{\"fileName\":\"com/parentpub/child/artifact/x.jar\"}")
          .assertStatus(403);
    } finally {
      db.deleteGroup("com.parentpub.child");
      db.deleteMember("com.parentpub", testUserId);
      db.deleteGroup("com.parentpub");
    }
  }

  @Test
  public void publish_fileNameOutsideNamespace_returns400() throws Exception {
    var string = new StringBodyAsserter();
    publish("org.lattejava", "{\"fileName\":\"com/evil/x.jar\"}")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"fieldErrors\"").contains("[outsideNamespace]fileName"));
  }

  @Test
  public void publish_missingFileName_returns400() throws Exception {
    var string = new StringBodyAsserter();
    publish("org.lattejava", "{}")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"fieldErrors\"").contains("[blank]fileName"));
  }

  @Test
  public void publish_malformedJSON_returns400() throws Exception {
    var string = new StringBodyAsserter();
    // Malformed JSON is a BadRequestException from JSONBodySupplier, rendered by web's default JSON renderer
    // (not the full Errors object the ValidationException renderer writes).
    publish("org.lattejava", "not json at all")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"error\"").contains("BadRequestException"));
  }

  @Test
  public void publish_uncleanKey_returns400() throws Exception {
    var string = new StringBodyAsserter();
    publish("org.lattejava", "{\"fileName\":\"org/lattejava/../secret.jar\"}")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"fieldErrors\"").contains("[uncleanKey]fileName"));
  }
}
