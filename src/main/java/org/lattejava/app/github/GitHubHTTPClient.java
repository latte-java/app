/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

import module java.base;
import module java.net.http;

import org.lattejava.app.github.internal.*;
import org.lattejava.app.internal.*;

public class GitHubHTTPClient implements GitHubClient {
  public static final String BASE_URL = "https://api.github.com";
  public static final String OAUTH_TOKEN_URL = "https://github.com/login/oauth/access_token";
  private final HttpClient httpClient;

  public GitHubHTTPClient() {
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public MembershipStatus checkOrgMembership(String accessToken, String org, String username) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/orgs/" + org + "/members/" + username))
                                     .timeout(Duration.ofSeconds(15))
                                     .header("Accept", "application/vnd.github+json")
                                     .header("Authorization", "Bearer " + accessToken)
                                     .header("X-GitHub-Api-Version", "2022-11-28")
                                     .GET()
                                     .build();
    HttpResponse<String> response = send(request, "checkOrgMembership", org + "/" + username);
    return switch (response.statusCode()) {
      case 204 -> MembershipStatus.MEMBER;
      case 401 -> MembershipStatus.UNAUTHORIZED;
      case 404, 302 -> MembershipStatus.NOT_MEMBER;
      default -> throw new GitHubException("GitHub /orgs/{org}/members/{user} returned HTTP ["
          + response.statusCode() + "] for [" + org + "/" + username + "]: [" + response.body() + "]");
    };
  }

  @Override
  public String exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectURI) {
    String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
        + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectURI, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder(URI.create(OAUTH_TOKEN_URL))
                                     .timeout(Duration.ofSeconds(15))
                                     .header("Accept", "application/json")
                                     .header("Content-Type", "application/x-www-form-urlencoded")
                                     .POST(HttpRequest.BodyPublishers.ofString(body))
                                     .build();
    HttpResponse<String> response = send(request, "exchangeCodeForToken", "(code redacted)");
    if (response.statusCode() / 100 != 2) {
      throw new GitHubException("GitHub token exchange returned HTTP [" + response.statusCode()
          + "]: [" + response.body() + "]");
    }

    try {
      TokenResponse tokenResponse = TokenResponseJSON.fromJSON(response.body());
      return tokenResponse.error() != null ? null : tokenResponse.accessToken();
    } catch (JSONProcessingException e) {
      throw new GitHubException("Failed to parse GitHub token exchange response", e);
    }
  }

  @Override
  public AccountType getAccountType(String accessToken, String name) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/users/" + name))
                                     .timeout(Duration.ofSeconds(15))
                                     .header("Accept", "application/vnd.github+json")
                                     .header("Authorization", "Bearer " + accessToken)
                                     .header("X-GitHub-Api-Version", "2022-11-28")
                                     .GET()
                                     .build();
    HttpResponse<String> response = send(request, "getAccountType", name);
    if (response.statusCode() == 401) {
      return AccountType.UNAUTHORIZED;
    }
    if (response.statusCode() == 404) {
      return AccountType.NOT_FOUND;
    }
    if (response.statusCode() / 100 != 2) {
      throw new GitHubException("GitHub /users/{name} returned HTTP [" + response.statusCode()
          + "] for [" + name + "]: [" + response.body() + "]");
    }
    String type;
    try {
      type = AccountResponseJSON.fromJSON(response.body()).type();
    } catch (JSONProcessingException e) {
      throw new GitHubException("Failed to parse GitHub /users response for [" + name + "]", e);
    }

    if (type == null) {
      throw new GitHubException("GitHub /users/{name} response for [" + name + "] missing [type] field");
    }

    return switch (type) {
      case "User" -> AccountType.USER;
      case "Organization" -> AccountType.ORGANIZATION;
      default -> throw new GitHubException("GitHub /users/{name} returned unexpected type ["
          + type + "] for [" + name + "]");
    };
  }

  @Override
  public String getLogin(String accessToken) {
    GitHubUser user = getUser(accessToken);
    return user == null ? null : user.login();
  }

  @Override
  public GitHubUser getUser(String accessToken) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/user"))
                                     .timeout(Duration.ofSeconds(15))
                                     .header("Accept", "application/vnd.github+json")
                                     .header("Authorization", "Bearer " + accessToken)
                                     .header("X-GitHub-Api-Version", "2022-11-28")
                                     .GET()
                                     .build();
    HttpResponse<String> response = send(request, "getUser", "(current user)");
    if (response.statusCode() == 401) {
      return null;
    }
    if (response.statusCode() / 100 != 2) {
      throw new GitHubException("GitHub /user returned HTTP [" + response.statusCode() + "]: [" + response.body() + "]");
    }

    try {
      GitHubUser user = GitHubUserJSON.fromJSON(response.body());
      return user.id() == 0 || user.login() == null ? null : user;
    } catch (JSONProcessingException e) {
      throw new GitHubException("Failed to parse GitHub /user response", e);
    }
  }

  private HttpResponse<String> send(HttpRequest request, String op, String detail) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new GitHubException("GitHub [" + op + "] failed for [" + detail + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GitHubException("GitHub [" + op + "] interrupted for [" + detail + "]", e);
    }
  }
}
