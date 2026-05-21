/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.security;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.model.Member;
import org.lattejava.app.model.MembershipState;
import org.lattejava.app.model.Role;

/**
 * Middleware that requires the authenticated user to be an ACTIVE member of the group identified by the
 * {@code groupName} path attribute with at least one of the listed roles. Modeled after
 * {@link org.lattejava.web.oidc.HasAnyRole}.
 * <p>
 * Reads the {@link Member} cached as the {@link GroupSecurity#MEMBER_ATTRIBUTE} request attribute by the paired
 * {@link GroupSecurity} middleware — so a route gated by both pays a single DB round-trip per request rather than
 * doubling up. {@code HasRole} therefore requires {@link GroupSecurity} to be installed earlier in the chain (the
 * standard pattern: {@code GroupSecurity} on the {@code /app/groups} prefix, {@code HasRole} per-route).
 * <p>
 * Responses:
 * <ul>
 *   <li>500 — misconfigured route: no {@code groupName} path attribute (developer bug, not user-facing)</li>
 *   <li>{@link IllegalStateException} — misconfigured chain: {@link GroupSecurity} was not installed ahead of this
 *       middleware, so no cached {@link Member} is available. Surfaced via {@code AppExceptionHandler}.</li>
 *   <li>303 → {@code /app/} — every authorization denial (wrong role, PENDING state) silently redirects the user
 *       home rather than leaking whether they lack a role</li>
 * </ul>
 *
 * @author Brian Pontarelli
 */
public class HasRole implements Middleware {
  private static final String GROUP_NAME_ATTRIBUTE = "groupName";

  private final Set<Role> required;

  HasRole(Role... roles) {
    if (roles == null || roles.length == 0) {
      throw new IllegalArgumentException("At least one role must be provided");
    }

    this.required = Set.of(roles);
  }

  @Override
  public void handle(HTTPRequest req, HTTPResponse res, MiddlewareChain chain) throws Exception {
    String groupName = (String) req.getAttribute(GROUP_NAME_ATTRIBUTE);
    if (groupName == null) {
      res.setStatus(500);
      return;
    }

    Member membership = (Member) req.getAttribute(GroupSecurity.MEMBER_ATTRIBUTE);
    if (membership == null) {
      throw new IllegalStateException(
          "HasRole requires GroupSecurity to be installed upstream; no cached membership for group [" + groupName + "]");
    }

    if (membership.state() != MembershipState.ACTIVE || !required.contains(membership.role())) {
      res.sendRedirect("/app/", 303);
      return;
    }

    chain.next(req, res);
  }
}
