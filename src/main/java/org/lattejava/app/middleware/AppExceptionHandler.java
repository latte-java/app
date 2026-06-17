/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.middleware;

import module java.base;
import module org.lattejava.web;

import org.lattejava.app.service.*;
import org.lattejava.fusionauth.*;

/**
 * Catches exceptions thrown by downstream handlers on the browser-facing routes and renders the shared
 * {@code pages/error.jte} page. Registered against {@link Exception} so it handles every exception: an
 * {@link HTTPException} renders with its carried status, anything else renders as a {@code 500}.
 * <p>
 * This is the outermost handler. The API routes install their own {@link APIExceptionHandler} (which renders JSON)
 * closer to the routes, so API exceptions are handled there and never reach this HTML renderer.
 *
 * @author Brian Pontarelli
 */
public class AppExceptionHandler extends ExceptionHandler {
  private static final System.Logger LOG = System.getLogger(AppExceptionHandler.class.getName());

  public AppExceptionHandler(JTETemplates templates) {
    super(
        Map.of(
            Exception.class, htmlRenderer(templates)
        )
    );
  }

  private static ErrorRenderer htmlRenderer(JTETemplates templates) {
    return (req, res, e) -> {
      LOG.log(System.Logger.Level.WARNING, "Encountered an uncaught exception [" + e + "]");
      if (e instanceof FusionAuthException fae) {
        LOG.log(System.Logger.Level.WARNING, "FusionAuth errors were [" + fae.errors.toJSON() + "]");
      }

      int status = (e instanceof HTTPException he) ? he.status() : 500;
      res.setStatus(status);
      templates.html("pages/error.jte", req, res, Map.of("status", status));
    };
  }
}
