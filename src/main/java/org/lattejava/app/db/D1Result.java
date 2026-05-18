/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;
import module com.fasterxml.jackson.databind;

@JsonIgnoreProperties(ignoreUnknown = true)
public record D1Result(
    List<Map<String, Object>> results,
    boolean success
) {
}
