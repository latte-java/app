/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.*;

public class ViewService {
  private final GroupService groupService;

  public ViewService(Configuration config) {
    this.groupService = new GroupService(config);
  }

  public MainView buildMainView(User viewer) {
    List<Group> groups = groupService.listForUser(viewer);
    return new MainView(viewer, groups, "dashboard", null, "light");
  }
}
