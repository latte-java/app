/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

import java.util.*;

public record InviteRequest(String groupName, String email, Role role) {
  public InviteRequest {
    groupName = groupName != null ? groupName.toLowerCase(Locale.ROOT) : null;
    email = email != null ? email.toLowerCase(Locale.ROOT) : null;
  }
}
