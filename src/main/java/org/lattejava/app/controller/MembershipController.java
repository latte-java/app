/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

public class MembershipController {
  public static final String GROUP_NAME = "groupName";
  public static final String USER_ID = "userId";
  private final GroupService groupService;
  private final MembershipService membershipService;
  private final OIDC<User> oidc;
  private final JTETemplates templates;
  private final ViewService viewService;

  public MembershipController(OIDC<User> oidc, JTETemplates templates) {
    this.oidc = oidc;
    this.templates = templates;
    this.groupService = Services.groupService();
    this.membershipService = Services.membershipService();
    this.viewService = Services.viewService();
  }

  public void accept(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    membershipService.acceptInvitation(groupName, userId);
    res.sendRedirect("/app/groups/" + groupName + "/", 303);
  }

  public void changeRole(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    Role newRole = Role.valueOf(req.getParameter("role"));
    User current = oidc.user();
    try {
      membershipService.changeRole(groupName, userId, newRole, current);
    } catch (ValidationException e) {
      // For Plan 04, errors on these admin actions are ignored at the controller level —
      // the UI prevents most invalid operations through button-disabled states.
    }

    res.sendRedirect("/app/groups/" + groupName + "/members", 303);
  }

  public void decline(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    membershipService.declineInvitation(groupName, userId);
    res.sendRedirect("/app/groups/" + groupName, 303);
  }

  public void invite(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    String email = req.getParameter("email");
    String roleParam = req.getParameter("role");
    Role role = roleParam == null ? Role.CONTRIBUTOR : Role.valueOf(roleParam);
    User current = oidc.user();
    try {
      membershipService.invite(new InviteRequest(groupName, email, role), current);
      res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
    } catch (ValidationException e) {
      renderMembers(req, res, groupName, email, role, e.errors());
    }
  }

  public void leave(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    User current = oidc.user();
    try {
      membershipService.leave(groupName, current);
    } catch (ValidationException e) {
      // Last-Owner protection — silent for now.
    }

    res.sendRedirect("/app/groups/", 303);
  }

  public void list(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    renderMembers(req, res, groupName, "", Role.CONTRIBUTOR, new Errors());
  }

  public void remove(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    User current = oidc.user();
    try {
      membershipService.remove(groupName, userId, current);
    } catch (ValidationException e) {
      // Self-rule and last-Owner protection — silent for now.
    }

    res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
  }

  private void renderMembers(HTTPRequest req, HTTPResponse res, String groupName,
                             String inviteEmail, Role inviteRole, Errors errors) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }
    Group group = groupOpt.get();
    MainView view = viewService.buildMainView(user);
    var members = membershipService.listMembers(groupName);
    templates.html("pages/groups/detail.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "activeTab", "members",
            "inviteEmail", inviteEmail == null ? "" : inviteEmail,
            "inviteRole", inviteRole == null ? "CONTRIBUTOR" : inviteRole.name(),
            "errors", errors,
            "members", members
        )
    );
  }
}
