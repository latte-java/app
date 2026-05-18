/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import org.lattejava.app.error.Errors;

public class ValidationException extends RuntimeException {
  private final Errors errors;

  public ValidationException(Errors errors) {
    super("Validation failed with [" + errors.size() + "] errors");
    this.errors = errors;
  }

  public Errors errors() {
    return errors;
  }
}
