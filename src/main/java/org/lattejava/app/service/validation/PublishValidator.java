/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import module java.base;

import org.lattejava.app.error.Errors;

/**
 * Validates a publish request body against the target group namespace. Group authorization itself is handled upstream
 * by {@link org.lattejava.app.security.PublishAuthorizer}; this only checks the shape of the requested object key.
 */
public class PublishValidator {
  /**
   * @param groupName The target namespace (already path-bound), used to derive the required key prefix.
   * @param fileName  The requested object key.
   * @return The collected errors; empty when the request is valid.
   */
  public Errors validate(String groupName, String fileName) {
    Errors errors = new Errors();
    if (fileName == null || fileName.isBlank()) {
      errors.addFieldError("fileName", "[blank]fileName", "A file name is required.");
      return errors;
    }

    String prefix = groupName.trim().toLowerCase(Locale.ROOT).replace('.', '/') + "/";
    if (!fileName.startsWith(prefix)) {
      errors.addFieldError("fileName", "[outsideNamespace]fileName",
          "The file name [%s] is not within the group namespace [%s].", fileName, groupName);
    }

    for (String segment : fileName.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        errors.addFieldError("fileName", "[uncleanKey]fileName",
            "The file name [%s] has an empty or relative path segment.", fileName);
        break;
      }
    }

    return errors;
  }
}
