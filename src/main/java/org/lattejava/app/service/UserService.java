package org.lattejava.app.service;

import org.lattejava.app.model.*;
import org.lattejava.jwt.*;

public class UserService {
  /**
   * Convert a JWT to a User.
   *
   * @param jwt The JWT to convert.
   * @return The User.
   */
  public static User toUser(JWT jwt) {
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

    return new User(email, name);
  }
}
