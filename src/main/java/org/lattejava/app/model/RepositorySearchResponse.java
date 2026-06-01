/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import module java.base;

/**
 * The JSON response for {@code GET /api/v1/repository/search}: the queried artifact id and its versions, sorted newest
 * first (or a single-element list when {@code latest=true}). An immutable carrier rendered directly to JSON.
 *
 * @author Brian Pontarelli
 */
public record RepositorySearchResponse(String id, List<String> versions) {
}
