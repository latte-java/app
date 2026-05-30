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
  public static final List<String> REQUIRED_CONFIG = List.of(
      "d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId",
      "fusionauth.apiKey", "fusionauth.baseUrl", "fusionauth.cliClientId", "fusionauth.cliClientSecret", "fusionauth.clientId", "fusionauth.clientSecret", "fusionauth.issuer",
      "github.clientId", "github.clientSecret",
      "s3.accessKeyId", "s3.bucket", "s3.endpoint", "s3.region", "s3.secretAccessKey",
      "web.cookieEncryptionKey"
  );
  public final OIDCConfig apiConfig;
  public final Configuration config;
  public final Cookies cookies;
  public final int port;
  public final OIDCConfig ssrConfig;
  public final BrowserSettings ssrSettings;
  public final JTETemplates templates;
  public final Web web;
  private final OIDC<User> apiOIDC;
  private final OIDC<User> ssrOIDC;

  public Main() {
    this(8080);
  }

  public Main(int port) {
    this.config = new Configuration(
        REQUIRED_CONFIG,
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );

    // OIDC setup
    this.ssrConfig = OIDCConfig.builder()
                               .issuer(config.get("fusionauth.issuer"))
                               .clientId(config.get("fusionauth.clientId"))
                               .clientSecret(config.get("fusionauth.clientSecret"))
                               .introspectionEndpoint(URI.create(config.get("fusionauth.baseUrl") + "/oauth2/introspect"))
                               .build();
    this.ssrSettings = BrowserSettings.builder()
                                      .postLoginPage("/app/")
                                      .postLogoutPage("https://lattejava.org")
                                      .build();
    this.ssrOIDC = OIDC.ssr(ssrConfig, ssrSettings, UserService::toUser);

    this.apiConfig = OIDCConfig.builder()
                               .issuer(config.get("fusionauth.issuer"))
                               .clientId(config.get("fusionauth.cliClientId"))
                               .clientSecret(config.get("fusionauth.cliClientSecret"))
                               .introspectionEndpoint(URI.create(config.get("fusionauth.baseUrl") + "/oauth2/introspect"))
                               .build();
    this.apiOIDC = OIDC.api(apiConfig, UserService::toUser);

    this.cookies = Cookies.encryptionKeys(Base64.getDecoder().decode(config.get("web.cookieEncryptionKey")));
    this.templates = new JTETemplates(BASE_DIR);
    this.port = port;
    this.web = new Web();
  }

  public void close() {
    web.close();
  }

  public void main() {
    Services.initialize(config);

    web.addShutdownTask(Services::shutdown)
       .baseDir(BASE_DIR)

       // Catch unhandled exceptions and render the fun error page. Installed first so it wraps every other middleware
       // and route handler.
       .install(new AppExceptionHandler(templates))

       // Allow the static resources (images, js, etc) to be used across origin (e.g., on the login page)
       .install(new FilteredMiddleware("/static", SecurityHeaders.empty().crossOriginResourcePolicy("cross-origin")))
       .files("/static")

       // Standard security but with our custom CSP URLs
       .install(SecurityHeaders.defaults().contentSecurityPolicy(CSP.defaults()
                                                                    .addImgSrc("https://gravatar.com")
                                                                    .build()))
       .install(OIDC.sessionEndpoints(ssrConfig, ssrSettings))
       .get("/", this::slash)
       .prefix("/app", app ->
           app.install(ssrOIDC.authenticated())
              .get("/", this::dashboard)
              .prefix("/oauth/github", gh -> {
                GitHubController gitHubController = new GitHubController(cookies, ssrOIDC);
                gh.get("/connect", gitHubController::startConnection)
                  .get("/callback", gitHubController::githubCallback);

              })
              .prefix("/groups", groupsRoute -> {
                GroupController groups = new GroupController(ssrOIDC, templates);
                MembershipController members = new MembershipController(ssrOIDC, templates);
                GroupSecurity groupSecurity = new GroupSecurity(ssrOIDC);
                Middleware isActiveMember = groupSecurity.hasRole(Role.OWNER, Role.CONTRIBUTOR);
                Middleware isOwner = groupSecurity.hasRole(Role.OWNER);

                // GroupSecurity is installed once at this literal /app/groups prefix and applies to every route under
                // it. For routes without a {groupName} path attribute (list, new, create) it is a pass-through; for
                // group-scoped routes it requires the authenticated user to have a membership row in that group
                // (PENDING or ACTIVE). Adding a new {groupName}-bearing route here is automatically gated.
                groupsRoute.install(groupSecurity)
                           .get("/", groups::list)
                           .get("/new", groups::newForm)
                           .post("/new", groups::create)
                           .get("/{groupName}/", groups::detail)
                           .get("/{groupName}/settings", groups::settings, isActiveMember)
                           .post("/{groupName}/settings", groups::updateSettings, isOwner)
                           .get("/{groupName}/verify", groups::verifyForm, isOwner)
                           .get("/{groupName}/delete", groups::deleteForm, isOwner)
                           .post("/{groupName}/delete", groups::delete, isOwner)
                           .post("/{groupName}/verify/check", groups::checkVerification, isOwner)
                           .post("/{groupName}/verify/github", groups::verifyGitHub, isOwner);

                // Member routes — owner-only admin actions add the stricter HasRole(OWNER) per-route; the base
                // membership check is already provided by the GroupSecurity install above. Accept/decline always act on
                // the authenticated user (no {userId} in the URL) and reach the controller for PENDING invitees
                // (GroupSecurity accepts PENDING rows). Leave reaches the controller for any member row including
                // PENDING — a PENDING leaver simply deletes their invitation row, equivalent to decline. The service
                // handles state transitions safely.
                groupsRoute.prefix("/{groupName}/members", membersRoute ->
                    membersRoute.get("/", members::list, isOwner)
                                .get("/invite", members::inviteForm, isOwner)
                                .post("/invite", members::invite, isOwner)
                                .post("/accept", members::accept)
                                .post("/decline", members::decline)
                                .get("/{userId}/remove", members::removeForm, isOwner)
                                .post("/{userId}/remove", members::remove, isOwner)
                                .get("/{userId}/role", members::changeRoleForm, isOwner)
                                .post("/{userId}/role", members::changeRole, isOwner)
                                .get("/leave", members::leaveForm)
                                .post("/leave", members::leave));
              })
       )
       .prefix("/api", api -> {
         PublishController publish = new PublishController();
         PublishAuthorizer publishAuthorizer = new PublishAuthorizer();
         api.install(new APIExceptionHandler())
            .install(apiOIDC.authenticated())
            .post("/v1/publish/{groupName}", publish::publish, JSONBodySupplier.of(PublishRequest.class), apiOIDC.authorized(publishAuthorizer));
       })
       .missingHandler(this::missing)
       .start(port);
  }

  private void dashboard(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = ssrOIDC.user();
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
