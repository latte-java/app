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

@Test
public class MainTest extends BaseTest {
  @Test
  public void alreadyLoggedInButRedirectsToOIDC() throws Exception {
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/login")
        .assertHeaderStartsWith("Location", "http://localhost:9011/oauth2/authorize");
  }

  @Test
  public void createGroup() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    String name = "create-group-" + UUID.randomUUID();
    test.get("/app/groups/new")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("<input"))
        .reset(ResetItem.Request)
        .withForm(
            Map.of(
                "name", name
            )
        )
        .post("/app/groups/new")
        .assertRedirect(303, "/app/groups/" + name + "/");
  }

  @Test
  public void dashboard() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/dashboard")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
  }

  @Test
  public void getSlash() {
    test.get("/")
        .assertRedirect(301, "/app/");
  }

  @Test
  public void groupDetail() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/groups/org.lattejava")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
  }

  @Test
  public void groupMembers() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/groups/org.lattejava/members/")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body").contains("org.lattejava"));
  }

  @Test
  public void oidcRedirect() {
    test.get("/app/dashboard")
        .assertRedirect(302, "/login")
        .reset(ResetItem.Request)
        .get("/login")
        .assertStatus(302)
        .assertHeaderStartsWith("Location", "http://localhost:9011/oauth2/authorize");
  }

  @Test
  public void updateSettings_redirectsOnSuccess() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    String name = "settings-ok-" + UUID.randomUUID();
    test.withForm(Map.of("name", name))
        .post("/app/groups/new")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .withForm(Map.of("description", "  a new description  "))
        .post("/app/groups/" + name + "/settings")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .get("/app/groups/" + name + "/settings")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("a new description"));
  }

  @Test
  public void updateSettings_rerendersWithErrorWhenTooLong() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    String name = "settings-toolong-" + UUID.randomUUID();
    String tooLong = "RETAINME-" + "x".repeat(492);
    test.withForm(Map.of("name", name))
        .post("/app/groups/new")
        .assertRedirect(303, "/app/groups/" + name + "/")
        .reset(ResetItem.Request)
        .withForm(Map.of("description", tooLong))
        .post("/app/groups/" + name + "/settings")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("Descriptions must be 500 characters or fewer").contains("RETAINME-"));
  }
}
