/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

/**
 * GitHub API access. Implementations should not throw checked exceptions; transport
 * failures surface as {@link GitHubException}. Authentication failures are conveyed
 * by returning {@link MembershipStatus#UNAUTHORIZED} from {@link #checkOrgMembership}
 * or by {@link #getLogin} returning {@code null}.
 */
public interface GitHubClient {
  /**
   * Checks whether {@code username} is a member of {@code org} via
   * {@code GET /orgs/{org}/members/{username}}.
   *
   * @param accessToken The user's GitHub OAuth token.
   * @param org         The GitHub organization name.
   * @param username    The GitHub login to check.
   * @return MEMBER (HTTP 204), NOT_MEMBER (404), UNAUTHORIZED (401).
   */
  MembershipStatus checkOrgMembership(String accessToken, String org, String username);

  /**
   * Exchanges an OAuth authorization code for an access token via
   * {@code POST https://github.com/login/oauth/access_token}.
   *
   * @param clientId     GitHub OAuth App client Id.
   * @param clientSecret GitHub OAuth App client secret.
   * @param code         The authorization code returned to the callback.
   * @param redirectURI  The redirect URI used on the authorize request; must match exactly.
   * @return The access token, or {@code null} if GitHub rejected the exchange.
   */
  String exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectURI);

  /**
   * Looks up {@code name} via {@code GET /users/{name}} and returns whether it identifies a
   * user, an organization, doesn't exist, or the request was unauthorized.
   *
   * @param accessToken The user's GitHub OAuth token. Used to avoid the 60/hr unauthenticated rate limit.
   * @param name        The GitHub login or organization name to resolve.
   * @return USER (type=User), ORGANIZATION (type=Organization), NOT_FOUND (404), UNAUTHORIZED (401).
   */
  AccountType getAccountType(String accessToken, String name);

  /**
   * Returns the GitHub login for the user owning {@code accessToken}, or {@code null}
   * if the token is invalid (401) or the response can't be parsed.
   */
  String getLogin(String accessToken);

  /**
   * Returns the GitHub user (numeric id + login) for the owner of {@code accessToken}, or
   * {@code null} if the token is invalid (401) or the response can't be parsed.
   */
  GitHubUser getUser(String accessToken);
}
