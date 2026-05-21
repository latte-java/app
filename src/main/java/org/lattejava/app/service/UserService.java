/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.jwt;

import org.lattejava.app.model.User;

public class UserService {
  /**
   * Convert a JWT to a User. The `sub` claim carries the FusionAuth user UUID; `preferred_username`
   * carries the FusionAuth username.
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
    String username = jwt.getString("preferred_username");
    return new User(userId, email, username);
  }

  /**
   * Convert a FusionAuth domain user to a User. Used to enrich members and invitees from FusionAuth
   * lookups.
   *
   * @param faUser The FusionAuth user.
   * @return The User.
   */
  public static User toUser(io.fusionauth.domain.User faUser) {
    return new User(faUser.id, faUser.email, faUser.username);
  }
}
