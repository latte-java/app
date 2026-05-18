/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model.view;

import module java.base;

import org.lattejava.app.model.*;

/**
 * DNS TXT verification challenge for a group's domain.
 */
public record VerificationView(
    String domain,
    String recordName,     // e.g. "_latte-verify.nimbusworks.io"
    String recordValue,    // e.g. "latte-verify=abcd1234ef..."
    Instant startedAt,
    Instant lastCheckedAt,
    boolean dnsRecordFound,
    boolean valueMatches
) {

  public static VerificationView buildView(Group group, GroupVerification verification) {
    String forward = forwardDomain(group.name());
    return new VerificationView(
        forward,
        forward,
        group.verificationCode() == null ? "" : group.verificationCode(),
        verification != null ? verification.startedAt() : null,
        verification != null ? verification.lastCheckedAt() : null,
        false,
        false
    );
  }

  public static String forwardDomain(String reverseDNSName) {
    String[] segments = reverseDNSName.split("\\.");
    StringBuilder out = new StringBuilder();
    for (int i = segments.length - 1; i >= 0; i--) {
      if (!out.isEmpty()) {
        out.append('.');
      }
      out.append(segments[i]);
    }
    return out.toString();
  }
}
