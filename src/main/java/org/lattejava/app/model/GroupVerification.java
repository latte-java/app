/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

public record GroupVerification(
    String groupName,
    Instant startedAt,
    Instant lastCheckedAt
) {
}
