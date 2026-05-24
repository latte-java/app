/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.s3;

public class S3Exception extends RuntimeException {
  public S3Exception(String message) {
    super(message);
  }

  public S3Exception(String message, Throwable cause) {
    super(message, cause);
  }
}
