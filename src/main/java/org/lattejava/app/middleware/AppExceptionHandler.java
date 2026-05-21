/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.middleware;

import module java.base;
import module org.lattejava.http;
import module org.lattejava.web;

/**
 * Catches exceptions thrown by downstream handlers and renders the shared {@code pages/error.jte} page. Any uncaught
 * {@link Exception} is mapped to a {@code 500}; the parent class's built-in {@link UnauthenticatedException} → 401
 * mapping is preserved.
 *
 * @author Brian Pontarelli
 */
public class AppExceptionHandler extends ExceptionHandler {
  private final JTETemplates templates;

  public AppExceptionHandler(JTETemplates templates) {
    super(Map.of(Exception.class, 500));
    this.templates = templates;
  }

  @Override
  protected void writeBody(HTTPRequest req, HTTPResponse res, Exception exception, int status) throws Exception {
    templates.html("pages/error.jte", req, res, Map.of("status", status));
  }
}
