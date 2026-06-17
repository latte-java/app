/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.middleware;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.error.Error;

/**
 * Renders JSON error responses for the API routes. Installed at the {@code /api} prefix, inside the browser-facing
 * {@link AppExceptionHandler}, so API exceptions are turned into JSON here rather than the HTML error page.
 * <p>
 * Every {@link HTTPException} (the {@code 400}/{@code 401}/{@code 403}/{@code 503} thrown by the body supplier and the
 * API auth middleware) is rendered by the framework's
 * {@link org.lattejava.web.middleware.ExceptionHandler#DEFAULT_RENDERER default JSON renderer}. Two app-specific
 * exceptions get their own renderers: a {@link ValidationException} becomes a {@code 400} whose body is the full
 * {@link Errors} object serialized as JSON (every field and general error), and an {@link S3Exception} becomes a
 * generic {@code 500} (the underlying message is not leaked). Add further per-type renderers here as the API grows.
 *
 * @author Brian Pontarelli
 */
public class APIExceptionHandler extends ExceptionHandler {
  public APIExceptionHandler() {
    super(Map.of(
        S3Exception.class, internalErrorRenderer(),
        ValidationException.class, validationRenderer()
    ));
  }

  private static ErrorRenderer internalErrorRenderer() {
    return (_, res, _) -> writeJSON(res, 500, new Error("InternalError", "The presigned URL could not be generated."));
  }

  private static ErrorRenderer validationRenderer() {
    return (_, res, e) -> {
      res.setStatus(400);
      res.setContentType("application/json");
      res.getWriter().write(((ValidationException) e).errors().toJSON());
    };
  }

  private static void writeJSON(HTTPResponse res, int status, Error error) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter().write(error.toJSON());
  }
}
