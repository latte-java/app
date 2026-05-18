/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.controller.*;
import org.lattejava.web.Configuration;

@SuppressWarnings("resource")
public class Main {
  public static final Path BASE_DIR = Path.of("web");
  public static final String CSP_HEADER = CSP.defaults()
                                             .addImgSrc("https://gravatar.com")
                                             .build();
  public static final int PORT = 8080;
  public static final List<String> REQUIRED_CONFIG = List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId",
      "fusionauth.apiKey", "fusionauth.baseUrl", "fusionauth.clientId", "fusionauth.clientSecret", "fusionauth.issuer",
      "fusionauth.licenseKey", "github.clientId", "github.clientSecret", "r2.accessKeyId", "r2.accountId",
      "r2.bucket", "r2.secretAccessKey", "web.cookieEncryptionKey");
  public final Configuration config;
  public final Cookies cookies;
  public final OIDC<User> oidc;
  public final OIDCConfig oidcConfig;
  public final JTETemplates templates;
  public final Web web;

  public Main() {
    config = new Configuration(
        REQUIRED_CONFIG,
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );

    oidcConfig = OIDCConfig.builder()
                           .issuer(config.get("fusionauth.issuer"))
                           .clientId(config.get("fusionauth.clientId"))
                           .clientSecret(config.get("fusionauth.clientSecret"))
                           .postLoginPage("/app/")
                           .postLogout("https://lattejava.org")
                           .build();
    oidc = OIDC.create(oidcConfig, UserService::toUser);
    cookies = Cookies.encryptionKeys(Base64.getDecoder().decode(config.get("web.cookieEncryptionKey")));
    templates = new JTETemplates(BASE_DIR, Path.of("build"));

    web = new Web();
  }

  public void close() {
    web.close();
  }

  public void main() {
    Services.initialize(config);

    web.addShutdownTask(Services::shutdown)
       .install(SecurityHeaders.builder()
                               .contentSecurityPolicy(CSP_HEADER)
                               .build())
       .install(oidc)
       .baseDir(BASE_DIR)
       .files("/static")
       .get("/", this::slash)
       .prefix("/app", app -> {
         app.install(oidc.authenticated())
            .get("/", this::dashboard)
            .prefix("/oauth/github", gh -> {
              GitHubController gitHubController = new GitHubController(cookies, oidc);
              gh.get("/connect", gitHubController::startConnection)
                .get("/callback", gitHubController::githubCallback);

            })
            .prefix("/groups", groupsRoute -> {
              // Set up the group routes and controller methods
              GroupController groups = new GroupController(oidc, templates);
              groupsRoute.get("/", groups::list)
                         .get("/new", groups::newForm)
                         .post("/new", groups::create)
                         .get("/{groupName}/", groups::detail)
                         .get("/{groupName}/settings", groups::settings)
                         .post("/{groupName}/settings", groups::updateSettings)
                         .get("/{groupName}/verify", groups::verifyForm)
                         .post("/{groupName}/delete", groups::delete)
                         .post("/{groupName}/verify/check", groups::checkVerification)
                         .post("/{groupName}/verify/github", groups::verifyGitHub);

              // Set up the membership routes and controller methods
              groupsRoute.prefix("/{groupName}/members", membersRoute -> {
                MembershipController members = new MembershipController(oidc, templates);
                membersRoute.get("/", members::list)
                            .post("/invite", members::invite)
                            .post("/{userId}/accept", members::accept)
                            .post("/{userId}/decline", members::decline)
                            .post("/{userId}/remove", members::remove)
                            .post("/{userId}/role", members::changeRole)
                            .post("/leave", members::leave);
              });
            });
       })
       .missingHandler(this::missing)
       .start(PORT);
  }

  private void dashboard(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    templates.html("pages/dashboard.jte", req, res,
        Map.of(
            "view", Services.viewService().buildMainView(user)
        )
    );
  }

  private void missing(HTTPRequest req, HTTPResponse res) throws IOException {
    res.setStatus(404);
    templates.html("pages/404.jte", req, res, Map.of());
  }

  private void slash(HTTPRequest req, HTTPResponse res) {
    res.sendRedirect("/app/", 301);
  }
}
