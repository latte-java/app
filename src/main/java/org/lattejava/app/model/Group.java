package org.lattejava.app.model;

import java.time.*;
import java.util.*;

/**
 * A repository group ("io.nimbusworks", "dev.jdoe", etc).
 */
public record Group(
    String id,                       // url slug, e.g. "io-nimbusworks"
    String name,                     // reverse-dns name, e.g. "io.nimbusworks"
    String domain,                   // forward dns, e.g. "nimbusworks.io" — null for handle groups
    VerificationStatus verification,
    Role viewerRole,                 // role the current viewer holds in this group
    String description,
    boolean handleGroup,             // true for "dev.<handle>" personal groups
    int artifactCount,
    int memberCount,
    long monthlyDownloads,
    LocalDate createdOn,
    LocalDate verifiedOn,            // null when not verified
    String pendingSince,             // free-text e.g. "Apr 26, 2026"
    List<Artifact> artifacts,
    List<Member> members,
    List<ActivityEntry> activity
) {
}
