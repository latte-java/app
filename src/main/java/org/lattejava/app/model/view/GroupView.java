/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model.view;

import org.lattejava.app.model.*;

/**
 * The chrome shared across every tab on the group detail page (Overview, Members, Settings). Built once per request by
 * {@link org.lattejava.app.service.ViewService#buildGroupView} and threaded through {@code detail.jte} and the per-tab
 * partials so each handler renders the same chrome from a single source.
 *
 * @author Brian Pontarelli
 */
public record GroupView(
    MainView mainView,
    Group group,
    Member viewerMembership,
    String activeTab
) {
  public String base() {
    return "/app/groups/" + group.name();
  }

  /**
   * Only OWNERs can manage the group.
   *
   * @return True if the user is a member, active, and an OWNER.
   */
  public boolean canManage() {
    return isActiveMember() && viewerMembership.role() == Role.OWNER;
  }

  /**
   * @return True if the user has an ACTIVE membership of any role.
   */
  public boolean isActiveMember() {
    return viewerMembership != null && viewerMembership.state() == MembershipState.ACTIVE;
  }

  public User viewer() {
    return mainView.viewer();
  }
}
