package org.lattejava.app.service;

import module org.lattejava.app;
import java.time.*;
import java.util.*;

public class ViewService {
  public View retrieve() {
    User viewer = new User("brian@pontarelli.com", "Brian Pontarelli");

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
    return new View(viewer, groups, "dashboard", null, "light");
  }
}
