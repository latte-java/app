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
 * HTML-level coverage of the tab visibility and Settings page content for the three viewer states (PENDING member,
 * active CONTRIBUTOR, active OWNER). PENDING sees Overview only; active CONTRIBUTOR sees Overview + Settings with the
 * Leave card only; active OWNER sees all three tabs and the full Settings management cards.
 */
@Test
public class SettingsViewTest extends BaseTest {
  private static final String APP_ID = "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e";

  @Test
  public void detail_activeContributor_hidesMembersShowsSettings() throws Exception {
    String name = "test.settings.view.tabs.contrib";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/settings\"")
                                      .doesNotContain("href=\"/app/groups/" + name + "/members/\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void detail_activeOwner_showsAllTabs() throws Exception {
    String name = "test.settings.view.tabs.owner";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/settings\"")
                                      .contains("href=\"/app/groups/" + name + "/members/\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void detail_pendingMember_hidesMembersAndSettings() throws Exception {
    String name = "test.settings.view.tabs.pending";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.OWNER, MembershipState.PENDING, null, Instant.now(), null));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.doesNotContain("href=\"/app/groups/" + name + "/settings\"")
                                      .doesNotContain("href=\"/app/groups/" + name + "/members/\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void leaveForm_activeContributor_rendersConfirmationWithCancelAndSubmit() throws Exception {
    String name = "test.settings.view.leave.contrib";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/members/leave")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Leave " + name)
                                      .contains("action=\"/app/groups/" + name + "/members/leave\"")
                                      .contains("href=\"/app/groups/" + name + "/settings\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void deleteForm_activeOwner_rendersConfirmationWithCancelAndSubmit() throws Exception {
    String name = "test.settings.view.delete.owner";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/delete")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("Delete " + name)
                                      .contains("action=\"/app/groups/" + name + "/delete\"")
                                      .contains("href=\"/app/groups/" + name + "/settings\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void settings_activeContributor_linksToLeaveConfirmationOnly() throws Exception {
    String name = "test.settings.view.page.contrib";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    db.insertMember(new Member(name, testUserId, Role.CONTRIBUTOR, MembershipState.ACTIVE, null, null, Instant.now()));
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/settings")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/members/leave\"")
                                      .doesNotContain("action=\"/app/groups/" + name + "/members/leave\"")
                                      .doesNotContain("action=\"/app/groups/" + name + "/settings\"")
                                      .doesNotContain("href=\"/app/groups/" + name + "/delete\"")
                                      .doesNotContain("action=\"/app/groups/" + name + "/delete\""));
    } finally {
      db.deleteGroup(name);
    }
  }

  @Test
  public void settings_activeOwner_linksToBothConfirmations() throws Exception {
    String name = "test.settings.view.page.owner";
    db.insertGroup(new Group(name, "", GroupState.VERIFIED, null, Instant.now(), Instant.now()));
    insertTestUserAsOwner(name);
    try {
      var string = new StringBodyAsserter();
      oidc.login("test@lattejava.org", "password", APP_ID);
      test.get("/app/groups/" + name + "/settings")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("href=\"/app/groups/" + name + "/members/leave\"")
                                      .contains("href=\"/app/groups/" + name + "/delete\"")
                                      .contains("action=\"/app/groups/" + name + "/settings\"")
                                      .doesNotContain("action=\"/app/groups/" + name + "/delete\""));
    } finally {
      db.deleteGroup(name);
    }
  }
}
