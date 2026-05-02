package org.lattejava.app.model;

public record Artifact(
    String name,           // e.g. "nimbus-core"
    String groupName,      // e.g. "io.nimbusworks"
    String latest,         // e.g. "4.2.1"
    long downloads30d,
    String lastPublished,  // free-text e.g. "2 days ago"
    String license         // e.g. "Apache-2.0"
) {
}
