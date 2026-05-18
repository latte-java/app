/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.jwt;

public class UserService {
  /**
   * Convert a JWT to a User. The `sub` claim carries the FusionAuth user UUID.
   *
   * @param jwt The JWT to convert.
   * @return The User.
   */
  public static User toUser(JWT jwt) {
    String sub = jwt.getString("sub");
    if (sub == null) {
      throw new IllegalStateException("JWT missing required [sub] claim");
    }
    UUID userId = UUID.fromString(sub);
    String email = jwt.getString("email");
    String name = jwt.getString("name");
    if (name == null || name.isBlank()) {
      String first = jwt.getString("given_name");
      String last = jwt.getString("family_name");
      name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    if (name.isBlank() && email != null) {
      name = email;
    }

    return new User(userId, email, name);
  }
}
