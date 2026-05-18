/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

/**
 * Outcome of completing the GitHub OAuth handshake started by {@code GroupController.connectGitHub} and
 * finishing in {@code OAuthController.githubCallback}.
 */
public enum GitHubLinkResult {
  /**
   * The GitHub OAuth handshake succeeded and the FusionAuth IdentityProviderLink was created.
   */
  LINKED,

  /**
   * The link operation with FusionAuth failed.
   */
  LINK_FAILED,

  /**
   * GitHub rejected the authorization code or refused to identify the user. The user should retry the connect flow.
   */
  OAUTH_FAILED
}
