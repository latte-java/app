/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

import module org.lattejava.json;

/**
 * Identifying details for a GitHub user, returned by {@code GET /user}. The {@code id} is the stable numeric account
 * Id used as the identityProviderUserId in FusionAuth IdentityProviderLink records; {@code login} is the GitHub
 * username (mutable).
 */
@JSON
public record GitHubUser(long id, String login) {
}
