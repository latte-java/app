/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.model.Member;

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
    membershipService.acceptInvitation(groupName, oidc.user().userId());
    res.sendRedirect("/app/groups/" + groupName + "/", 303);
  }

  public void changeRole(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    Role newRole = Role.valueOf(req.getParameter("role"));
    User current = oidc.user();
    try {
      membershipService.changeRole(groupName, userId, newRole, current);
      res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
    } catch (ValidationException e) {
      renderRoleForm(req, res, groupName, userId, newRole, e.errors());
    }
  }

  public void changeRoleForm(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    renderRoleForm(req, res, groupName, userId, null, new Errors());
  }

  public void decline(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    membershipService.declineInvitation(groupName, oidc.user().userId());
    res.sendRedirect("/app/groups/", 303);
  }

  public void invite(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    String email = req.getParameter("email");
    String roleParam = req.getParameter("role");
    Role role = roleParam == null ? Role.CONTRIBUTOR : Role.valueOf(roleParam);
    InviteRequest request = new InviteRequest(groupName, email, role);
    User current = oidc.user();
    try {
      membershipService.invite(request, current);
      res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
    } catch (ValidationException e) {
      renderInviteForm(req, res, groupName, request, e.errors());
    }
  }

  public void inviteForm(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    renderInviteForm(req, res, groupName, new InviteRequest(groupName, "", Role.CONTRIBUTOR), new Errors());
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

  public void leaveForm(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    GroupView groupView = viewService.buildGroupView(user, groupOpt.get(), "settings");
    templates.html("pages/groups/leave.jte", req, res, Map.of("groupView", groupView));
  }

  public void list(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    renderMembers(req, res, groupName);
  }

  public void remove(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    User current = oidc.user();
    try {
      membershipService.remove(groupName, userId, current);
      res.sendRedirect("/app/groups/" + groupName + "/members/", 303);
    } catch (ValidationException e) {
      renderRemoveForm(req, res, groupName, userId, e.errors());
    }
  }

  public void removeForm(HTTPRequest req, HTTPResponse res) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    UUID userId = UUID.fromString((String) req.getAttribute(USER_ID));
    renderRemoveForm(req, res, groupName, userId, new Errors());
  }

  private void renderInviteForm(HTTPRequest req, HTTPResponse res, String groupName,
                                InviteRequest request, Errors errors) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/invite.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "inviteRequest", request,
            "errors", errors
        )
    );
  }

  private void renderMembers(HTTPRequest req, HTTPResponse res, String groupName) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    GroupView groupView = viewService.buildGroupView(user, group, "members");
    var members = membershipService.listMembers(groupName);
    templates.html("pages/groups/detail.jte", req, res,
        Map.of(
            "groupView", groupView,
            "members", members
        )
    );
  }

  private void renderRemoveForm(HTTPRequest req, HTTPResponse res, String groupName, UUID userId,
                                Errors errors) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    Optional<Member> memberOpt = membershipService.findEnrichedMember(groupName, userId);
    if (memberOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Member member = memberOpt.get();
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/remove.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "member", member,
            "errors", errors
        )
    );
  }

  private void renderRoleForm(HTTPRequest req, HTTPResponse res, String groupName, UUID userId,
                              Role selectedRole, Errors errors) throws IOException {
    User user = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Group group = groupOpt.get();
    Optional<Member> memberOpt = membershipService.findEnrichedMember(groupName, userId);
    if (memberOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    Member member = memberOpt.get();
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/role.jte", req, res,
        Map.of(
            "view", view,
            "group", group,
            "member", member,
            "selectedRole", selectedRole != null ? selectedRole : member.role(),
            "errors", errors
        )
    );
  }
}
