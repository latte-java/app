/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

/**
 * A repository group. The {@code name} is the identity (no synthetic UUID).
 */
public record Group(
    String name,
    String description,
    GroupState state,
    String verificationCode,
    Instant createdAt,
    Instant verifiedAt
) {
  public Group {
    name = name != null ? name.trim().toLowerCase(Locale.ROOT) : null;
    description = description != null ? description.trim() : "";
    state = state != null ? state : GroupState.PENDING;
    createdAt = createdAt != null ? createdAt : Instant.EPOCH;
  }
}
