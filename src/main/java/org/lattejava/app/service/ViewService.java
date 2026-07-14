/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.Member;
import org.lattejava.web.*;

public class ViewService {
  private final String accountURL;
  private final GroupService groupService;
  private final MembershipService membershipService;

  public ViewService(Configuration config) {
    this.groupService = Services.groupService();
    this.membershipService = Services.membershipService();

    String base = config.get("fusionauth.baseURL");
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }

    this.accountURL = base + "/account/?client_id=" + config.get("fusionauth.clientId");
  }

  public GroupView buildGroupView(User user, Group group, String activeTab) {
    Member member = membershipService.findMember(group.name(), user.userId())
                                     .orElseThrow(() -> new IllegalStateException("No membership row for user [" + user.userId() + "] in group [" + group.name() + "]"));
    return new GroupView(buildMainView(user), group, member, activeTab);
  }

  public MainView buildMainView(User viewer) {
    List<Group> groups = groupService.listForUser(viewer);
    return new MainView(viewer, groups, "dashboard", null, accountURL);
  }
}
