/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.security;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.jwt;
import module org.lattejava.web;

import org.lattejava.app.model.*;
import org.lattejava.app.service.*;

/**
 * Decides whether a validated API caller may publish to the group named in the request path. Resolves the most specific
 * registered group that owns the namespace (see {@link GroupService#findOwningGroup(String)}), then requires that group
 * to be {@link GroupState#VERIFIED} and the caller to hold an {@link MembershipState#ACTIVE} membership in it with the
 * {@link Role#OWNER} or {@link Role#CONTRIBUTOR} role. The role test is a positive set membership so future roles
 * default to not-permitted.
 * <p>
 * Installed per-route via {@link org.lattejava.web.oidc.OIDC#authorized}, so it runs after authentication (a decoded
 * JWT is bound) and after route matching (the {@code groupName} path attribute is set).
 *
 * @author Brian Pontarelli
 */
public class PublishAuthorizer implements Authorizer {
  private static final String GROUP_NAME_ATTRIBUTE = "groupName";
  private static final Set<Role> PUBLISH_ROLES = Set.of(Role.CONTRIBUTOR, Role.OWNER);
  private final GroupService groupService;
  private final MembershipService membershipService;

  public PublishAuthorizer() {
    this.groupService = Services.groupService();
    this.membershipService = Services.membershipService();
  }

  @Override
  public boolean authorize(HTTPRequest req, JWT jwt) {
    String groupName = (String) req.getAttribute(GROUP_NAME_ATTRIBUTE);
    if (groupName == null || groupName.isBlank()) {
      return false;
    }

    Optional<Group> owningGroup = groupService.findOwningGroup(groupName);
    if (owningGroup.isEmpty() || owningGroup.get().state() != GroupState.VERIFIED) {
      return false;
    }

    UUID userId = UserService.toUser(jwt).userId();
    Optional<Member> member = membershipService.findMember(owningGroup.get().name(), userId);
    return member.isPresent()
        && member.get().state() == MembershipState.ACTIVE
        && PUBLISH_ROLES.contains(member.get().role());
  }
}
