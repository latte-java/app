/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.r2;

public class R2Exception extends RuntimeException {
  public R2Exception(String message) {
    super(message);
  }

  public R2Exception(String message, Throwable cause) {
    super(message, cause);
  }
}
