/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

public record D1Request(String sql, List<Object> params) {
}
