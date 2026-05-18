/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module com.fasterxml.jackson.databind;
import module java.base;

@JsonIgnoreProperties(ignoreUnknown = true)
public record D1Error(int code, String message) {
}
