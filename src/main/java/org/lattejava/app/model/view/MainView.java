/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model.view;

import module java.base;
import module org.lattejava.app;

/**
 * Bound on every page, holds chrome state.
 */
public record MainView(
    User viewer,
    List<Group> groupsForSidebar,
    String activeNav,      // "dashboard" | "groups" | "artifacts" | "settings" | ...
    String activeGroupId,  // nullable
    String accountURL      // FusionAuth self-service account-management URL
) {
}
