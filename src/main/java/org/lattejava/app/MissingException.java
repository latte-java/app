/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app;

import org.lattejava.web.*;

/**
 * A marker exception for missing values (404).
 *
 * @author Brian Pontarelli
 */
public class MissingException extends HTTPException {
  public MissingException() {
    super(404);
  }

  public MissingException(String message) {
    super(404, message);
  }

  public MissingException(String message, Throwable cause) {
    super(404, message, cause);
  }
}
