/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

import module org.lattejava.json;

/**
 * The slice of the JSON body GitHub returns from {@code GET /users/{name}} that {@link GitHubClient#getAccountType}
 * needs. The full response is a large user/organization object; only the {@code type} discriminator matters here.
 *
 * @param type The account type — {@code User} or {@code Organization}.
 */
@JSON
public record AccountResponse(String type) {
}
