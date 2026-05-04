package org.lattejava.app;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.jwt;
import module org.lattejava.web;

import org.lattejava.web.Configuration;

@SuppressWarnings("resource")
public class Main {
  public static final Path BASE_DIR = Path.of("web");
  public static final int PORT = 8080;
  public final Configuration config;
  public final OIDC<User> oidc;
  public final OIDCConfig oidcConfig;
  public final JTETemplates templates;
  public final Web web;

  public Main() {
    // Production will override this config file with ENV vars to the Docker container. Setting the config file path
    // like this makes running and testing in dev much simpler
    config = new Configuration(
        Path.of("src/test/resources/config.properties"),
        List.of("fusionauth.issuer", "fusionauth.clientId", "fusionauth.clientSecret")
    );

    oidcConfig = OIDCConfig.builder()
                           .issuer(config.get("fusionauth.issuer"))
                           .clientId(config.get("fusionauth.clientId"))
                           .clientSecret(config.get("fusionauth.clientSecret"))
                           .postLoginPage("/app/dashboard")
                           .postLogout("https://lattejava.org")
                           .build();
    oidc = OIDC.create(oidcConfig, UserService::toUser);
    templates = new JTETemplates(BASE_DIR, Path.of("build"));
    web = new Web();
  }

  public void close() {
    web.close();
  }

  public void main() {
    web.install(SecurityHeaders.builder()
                               .contentSecurityPolicy("default-src 'self'; "
                                   + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                   + "font-src 'self' https://fonts.gstatic.com; "
                                   + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; "
                                   + "form-action 'self'")
                               .build())
       .install(oidc)
       .baseDir(BASE_DIR)
       .files("/static")
       .get("/", this::slash)
       .prefix("/app", r -> {
         r.install(oidc.authenticated());
         r.get("/dashboard", this::dashboard);
       })
       .start(PORT);
  }

  private void dashboard(HTTPRequest req, HTTPResponse res) throws IOException {
    var viewService = new ViewService();
    templates.html("pages/dashboard.jte", req, res,
        Map.of(
            "view", viewService.retrieve()
        )
    );
  }

  private void slash(HTTPRequest req, HTTPResponse res) {
    res.sendRedirect("/app/dashboard", 301);
  }
}
