package org.lattejava.app.model;

/**
 * DNS TXT verification challenge for a group's domain.
 */
public record VerificationChallenge(
    String domain,
    String recordName,     // e.g. "_latte-verify.nimbusworks.io"
    String recordValue,    // e.g. "latte-verify=abcd1234ef..."
    String startedAt,
    String lastCheckedAt,
    boolean dnsRecordFound,
    boolean valueMatches
) {
}
