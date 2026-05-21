/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record Member(
    String groupName,
    User user,
    Role role,
    MembershipState state,
    UUID invitedBy,
    Instant invitedAt,
    Instant joinedAt
) {
  /**
   * Convenience constructor for callers that only have the user's UUID (the D1 read path and tests).
   * The email and username are left null until the member is enriched from FusionAuth.
   */
  public Member(String groupName, UUID userId, Role role, MembershipState state, UUID invitedBy,
                Instant invitedAt, Instant joinedAt) {
    this(groupName, new User(userId, null, null), role, state, invitedBy, invitedAt, joinedAt);
  }

  /**
   * @return The member's FusionAuth user UUID.
   */
  public UUID userId() {
    return user.userId();
  }
}
