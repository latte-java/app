/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.github;

public class GitHubException extends RuntimeException {
  public GitHubException(String message) {
    super(message);
  }

  public GitHubException(String message, Throwable cause) {
    super(message, cause);
  }
}
