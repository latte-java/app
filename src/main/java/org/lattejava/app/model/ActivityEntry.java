/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

public record ActivityEntry(
    Kind kind,
    String actor,          // display name of the actor (e.g. "Mira" or "mira@nimbusworks.io")
    String text,
    String when,
    String groupName       // null when viewed inside a group
) {
  public enum Kind {PUBLISH, MEMBER, VERIFY}

  public String tone() {
    return switch (kind) {
      case PUBLISH -> "cup";
      case VERIFY -> "success";
      case MEMBER -> "neutral";
    };
  }

  public String icon() {
    return switch (kind) {
      case PUBLISH -> "package";
      case MEMBER -> "users";
      case VERIFY -> "shield";
    };
  }

  public String group() {
    return groupName;
  }
}
