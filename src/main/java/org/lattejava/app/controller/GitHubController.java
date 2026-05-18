/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

/**
 * Handles OAuth interactions between the Latte app and GitHub.
 * <p>
 * This can start a GitHub linking that will associate the user's FusionAuth account with their GitHub account.
 * <p/>
 * It also handles the callback from GitHub, which completes the OAuth code exchange.
 * <p/>
 * Everything is authenticated because the access token from OIDC is Lax, which means this controller will have access
 * to the current user.
 *
 * @author Brian Pontarelli
 */
public class GitHubController {
  public static final String GITHUB_CALLBACK_PATH = "/app/oauth/github/callback";
  public static final String GITHUB_OAUTH_STATE_COOKIE = "github_oauth_state";
  private final Cookies cookies;
  private final OIDC<User> oidc;
  private final VerificationService verificationService;

  public GitHubController(Cookies cookies, OIDC<User> oidc) {
    this.cookies = cookies;
    this.oidc = oidc;
    this.verificationService = Services.verificationService();
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }

  public void githubCallback(HTTPRequest req, HTTPResponse res) {
    String code = req.getURLParameter("code");
    String state = req.getURLParameter("state");

    String groupName;
    try {
      groupName = cookies.read(GITHUB_OAUTH_STATE_COOKIE)
                         .encrypted()
                         .from(req);
    } catch (CookieIntegrityException e) {
      res.sendRedirect("/app/groups/?status=oauth_failed", 303);
      return;
    }

    cookies.clear(GITHUB_OAUTH_STATE_COOKIE)
           .path("/app/oauth")
           .from(req, res);

    if (!constantTimeEquals(groupName, state)) {
      res.sendRedirect("/app/groups/?status=oauth_failed", 303);
      return;
    }

    var redirectURI = req.getBaseURL() + GITHUB_CALLBACK_PATH;
    var userId = oidc.user().userId();
    var result = verificationService.linkGitHub(userId, code, redirectURI);
    res.sendRedirect("/app/groups/" + groupName + "/verify?status=" + result.name().toLowerCase(), 303);
  }

  public void startConnection(HTTPRequest req, HTTPResponse res) {
    var groupName = req.getURLParameter("groupName");
    if (groupName == null) {
      res.sendRedirect("/app/groups/?status=oauth_failed", 303);
      return;
    }

    cookies.write(GITHUB_OAUTH_STATE_COOKIE, groupName)
           .encrypted()
           .path("/app/oauth")
           .sameSite(Cookie.SameSite.Lax)
           .to(req, res);

    String redirectURI = req.getBaseURL() + GITHUB_CALLBACK_PATH;
    res.sendRedirect(verificationService.buildGitHubAuthorizeURL(groupName, redirectURI), 302);
  }
}
