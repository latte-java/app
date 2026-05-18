/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record Member(
    String groupName,
    UUID userId,
    Role role,
    MembershipState state,
    UUID invitedBy,
    Instant invitedAt,
    Instant joinedAt
) {
}
