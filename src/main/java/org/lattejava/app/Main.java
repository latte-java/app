package org.lattejava.app;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.jwt;
import module org.lattejava.web;

import org.lattejava.app.model.*;
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
    oidc = OIDC.create(oidcConfig, Main::toUser);
    templates = new JTETemplates(BASE_DIR, Path.of("build"));
    web = new Web();
  }

  private static String initials(String name, String email) {
    if (name != null && !name.isBlank() && !name.equals(email)) {
      var parts = name.trim().split("\\s+");
      if (parts.length >= 2) {
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
      }
      return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }
    if (email != null && email.length() >= 2) {
      return email.substring(0, 2).toUpperCase();
    }
    return "??";
  }

  private static User toUser(JWT jwt) {
    String email = jwt.getString("email");
    String name = jwt.getString("name");
    if (name == null || name.isBlank()) {
      String first = jwt.getString("given_name");
      String last = jwt.getString("family_name");
      name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
    if (name.isBlank() && email != null) {
      name = email;
    }
    return new User(email, name, initials(name, email));
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
       .get("/", this::redirect)
       .prefix("/app", r -> {
         r.install(oidc.authenticated());
         r.get("/dashboard", this::dashboard);
       })
       .start(PORT);
  }

  private void dashboard(HTTPRequest req, HTTPResponse res) throws IOException {
    User viewer = new User("brian@pontarelli.com", "Brian Pontarelli", "BP");

    List<Artifact> nimbusArtifacts = List.of(
        new Artifact("nimbus-core", "io.nimbusworks", "4.2.1", 184_230, "2 days ago", "Apache-2.0"),
        new Artifact("nimbus-web", "io.nimbusworks", "4.2.0", 82_400, "5 days ago", "Apache-2.0"),
        new Artifact("nimbus-cli", "io.nimbusworks", "1.7.3", 12_800, "3 weeks ago", "Apache-2.0")
    );

    List<Member> nimbusMembers = List.of(
        new Member("brian@pontarelli.com", "Brian Pontarelli", Role.OWNER, "BP", "Mar 12, 2026", false),
        new Member("mira@nimbusworks.io", "Mira Chen", Role.ADMIN, "MC", "Mar 14, 2026", false),
        new Member("jdoe@example.com", null, Role.PUBLISHER, "JD", "", true)
    );

    List<ActivityEntry> activity = List.of(
        new ActivityEntry(ActivityEntry.Kind.PUBLISH, "Brian Pontarelli",
            "published <strong>nimbus-core 4.2.1</strong>", "2 hours ago", "io.nimbusworks"),
        new ActivityEntry(ActivityEntry.Kind.MEMBER, "Mira Chen",
            "invited <strong>jdoe@example.com</strong> as Publisher", "yesterday", "io.nimbusworks"),
        new ActivityEntry(ActivityEntry.Kind.VERIFY, "Latte",
            "verified <strong>nimbusworks.io</strong>", "Apr 26, 2026", "io.nimbusworks"),
        new ActivityEntry(ActivityEntry.Kind.PUBLISH, "Brian Pontarelli",
            "published <strong>nimbus-web 4.2.0</strong>", "5 days ago", "io.nimbusworks")
    );

    Group nimbus = new Group(
        "io-nimbusworks", "io.nimbusworks", "nimbusworks.io",
        VerificationStatus.VERIFIED, Role.OWNER,
        "Nimbus Works' open source libraries.",
        false, nimbusArtifacts.size(), nimbusMembers.size(), 279_430L,
        LocalDate.of(2025, 11, 1), LocalDate.of(2026, 4, 26),
        null, nimbusArtifacts, nimbusMembers, activity
    );

    Group personal = new Group(
        "dev-bpontarelli", "dev.bpontarelli", null,
        VerificationStatus.NOT_APPLICABLE, Role.OWNER,
        "Personal handle group for prototypes.",
        true, 2, 1, 1_540L,
        LocalDate.of(2025, 9, 15), null,
        null, List.of(), List.of(), List.of()
    );

    Group pendingExample = new Group(
        "com-example", "com.example", "example.com",
        VerificationStatus.PENDING, Role.ADMIN,
        "Example sandbox group.",
        false, 0, 2, 0L,
        LocalDate.of(2026, 4, 28), null,
        "Apr 28, 2026", List.of(), List.of(), List.of()
    );

    List<Group> groups = List.of(nimbus, personal, pendingExample);
    View view = new View(viewer, groups, "dashboard", null, "light");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("view", view);
    params.put("groups", groups);
    params.put("recentActivity", activity);
    templates.html("pages/dashboard.jte", req, res, params);
  }

  private void redirect(HTTPRequest req, HTTPResponse res) {
    res.sendRedirect("/app/dashboard", 301);
  }
}
