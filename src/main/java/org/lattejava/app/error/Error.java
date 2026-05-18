/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.error;

import module java.base;

/**
 * A single error: a code, a fully-formatted human-readable message, and optional structured data.
 * Format-string values supplied to {@link Errors#addFieldError} / {@link Errors#addGeneralError}
 * are applied at construction time and only the resulting string is stored on the error.
 */
public class Error {
  public String code;
  public String message;

  public Error() {
  }

  public Error(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Error error = (Error) o;
    return Objects.equals(code, error.code)
        && Objects.equals(message, error.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message);
  }

  @Override
  public String toString() {
    return "Error[code=[" + code + "], message=[" + message + "]]";
  }
}
