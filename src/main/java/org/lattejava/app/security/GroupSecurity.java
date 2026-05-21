/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.security;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.model.Group;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.Role;
import org.lattejava.app.model.User;
import org.lattejava.app.service.GroupService;
import org.lattejava.app.service.MembershipService;

/**
 * The group security middleware. Installed once on the {@code /app/groups} prefix so every group-scoped route under
 * it is automatically gated — there is no per-route opt-in to forget. Two phases:
 * <ol>
 *   <li>If the matched route has no {@code groupName} path attribute (e.g. {@code GET /app/groups/} or
 *       {@code /new}), the request is not group-scoped and passes through unchanged.</li>
 *   <li>Otherwise the group must exist and the authenticated user must have a membership row in it. Any failure
 *       silently redirects to {@code /app/} (303) — no leaking whether the group exists vs the user just lacks a
 *       role.</li>
 * </ol>
 * <p>
 * "Has a membership row" intentionally includes PENDING invitees so they can view the group's overview to find the
 * Accept/Decline buttons and so {@code POST .../accept|/decline} reaches the controller. The stricter role-scoped
 * gates (see {@link #hasRole(Role...)}) reject non-ACTIVE rows and wrong roles on top of this base check.
 * <p>
 * Note: {@link org.lattejava.web.Web#install(Middleware...)} matches LITERAL path segments only, so it is installed
 * at the literal {@code /app/groups} prefix. {@code {groupName}} is bound by route matching before middleware runs,
 * so the attribute lookup here is reliable.
 *
 * @author Brian Pontarelli
 */
public class GroupSecurity implements Middleware {
  /**
   * Request attribute key under which the resolved {@link org.lattejava.app.model.Group} is cached after a successful
   * pass through this middleware. Downstream middlewares ({@link HasRole}) and handlers can read it to avoid a second
   * DB round-trip; readers that depend on it must be installed downstream of this middleware.
   */
  public static final String GROUP_ATTRIBUTE = "groupSecurity.group";

  /**
   * Request attribute key under which the resolved {@link Member} (the authenticated user's row in the path-bound
   * group) is cached after a successful pass through this middleware. May be PENDING or ACTIVE; downstream consumers
   * are responsible for any stricter state check.
   */
  public static final String MEMBER_ATTRIBUTE = "groupSecurity.member";

  private static final String GROUP_NAME_ATTRIBUTE = "groupName";

  private final GroupService groupService;
  private final MembershipService membershipService;
  private final OIDC<User> oidc;

  public GroupSecurity(OIDC<User> oidc, GroupService groupService, MembershipService membershipService) {
    this.oidc = oidc;
    this.groupService = groupService;
    this.membershipService = membershipService;
  }

  @Override
  public void handle(HTTPRequest req, HTTPResponse res, MiddlewareChain chain) throws Exception {
    String groupName = (String) req.getAttribute(GROUP_NAME_ATTRIBUTE);
    if (groupName == null) {
      chain.next(req, res);
      return;
    }

    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.sendRedirect("/app/", 303);
      return;
    }

    User user = oidc.user();
    Optional<Member> membershipOpt = membershipService.findMember(groupName, user.userId());
    if (membershipOpt.isEmpty()) {
      res.sendRedirect("/app/", 303);
      return;
    }

    req.setAttribute(GROUP_ATTRIBUTE, groupOpt.get());
    req.setAttribute(MEMBER_ATTRIBUTE, membershipOpt.get());
    chain.next(req, res);
  }

  /**
   * @param roles One or more roles; the returned middleware lets the request through when the authenticated user has
   *              an ACTIVE membership in the path-bound group with at least one of the listed roles. Attach as a
   *              per-route middleware on owner-only endpoints; the base membership check is already handled by this
   *              {@code GroupSecurity} instance being installed at the prefix, and {@link HasRole} reuses the cached
   *              {@link Member} attribute set here rather than re-querying the DB.
   * @return A new {@link HasRole} middleware bound to this {@code GroupSecurity} instance.
   */
  public HasRole hasRole(Role... roles) {
    return new HasRole(roles);
  }
}
