package org.lattejava.app.model;

import java.util.*;

/**
 * Bound on every page, holds chrome state.
 */
public record View(
    User viewer,
    List<Group> groupsForSidebar,
    String activeNav,      // "dashboard" | "groups" | "artifacts" | "settings" | ...
    String activeGroupId,  // nullable
    String theme           // "light" | "dark"
) {
}
