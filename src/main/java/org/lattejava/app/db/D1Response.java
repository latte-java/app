/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;
import module com.fasterxml.jackson.databind;

@JsonIgnoreProperties(ignoreUnknown = true)
public record D1Response(
    boolean success,
    List<D1Error> errors,
    List<D1Result> result
) {
}
