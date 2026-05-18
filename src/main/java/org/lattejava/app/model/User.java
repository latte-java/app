/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

/**
 * The signed-in user, threaded into the main layout via the View shell.
 */
public record User(
    UUID userId,            // FusionAuth user UUID (sub claim)
    String email,           // primary identifier — there are no usernames
    String name
) {
}
