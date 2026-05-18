/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

/**
 * Identifying details for a GitHub user, returned by {@code GET /user}. The {@code id} is the stable numeric account
 * Id used as the identityProviderUserId in FusionAuth IdentityProviderLink records; {@code login} is the GitHub
 * username (mutable).
 */
public record GitHubUser(long id, String login) {
}
