/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;

import org.lattejava.app.model.PublishRequest;
import org.lattejava.app.model.PublishResponse;
import org.lattejava.app.service.PublishService;
import org.lattejava.app.service.Services;

/**
 * Handles the publish endpoints. {@code POST /api/v1/publish/{groupName}} asks {@link PublishService} for a presigned
 * PUT URL and returns it as JSON; the request body is parsed into a {@link PublishRequest} by a
 * {@code BodySupplier.of(PublishRequest::fromJSON)} on the route, so that method is a
 * {@link org.lattejava.web.BodyHandler}. {@code GET /api/v1/publish/{groupName}} is a
 * bodyless permission pre-check (see {@link #precheck}); the HTTP server's automatic HEAD-to-GET rewrite means the CLI
 * can issue it as a {@code HEAD}. Authentication and group authorization run upstream as middleware for both (see
 * {@link org.lattejava.app.security.PublishAuthorizer}). Error responses are rendered by the
 * {@code /api}
 * {@link org.lattejava.app.middleware.APIExceptionHandler}: a malformed body throws
 * {@link org.lattejava.web.BadRequestException} from the supplier, and validation/presign failures throw exceptions
 * this method lets propagate, so the happy path is all that lives here.
 *
 * @author Brian Pontarelli
 */
public class PublishController {
  private static final String GROUP_NAME = "groupName";
  private final PublishService publishService;

  public PublishController() {
    this.publishService = Services.publishService();
  }

  /**
   * Backs {@code GET /api/v1/publish/{groupName}} and, by the HTTP server's automatic HEAD-to-GET rewrite, the
   * {@code HEAD} pre-check the CLI runs before attempting a publish. Authentication and group authorization run upstream
   * as middleware (the same {@code authenticated()} + {@link org.lattejava.app.security.PublishAuthorizer} chain as
   * {@link #publish}), so reaching this method means the caller's token is valid and they may publish to the group.
   * There is nothing to validate and no URL to mint, so it simply returns {@code 200} with an empty body (the server
   * suppresses even that for a HEAD request); a failed token returns {@code 401} and a failed authorization
   * {@code 403}, both from the upstream middleware.
   *
   * @param req The request.
   * @param res The response.
   */
  public void precheck(HTTPRequest req, HTTPResponse res) {
    res.setStatus(200);
  }

  public void publish(HTTPRequest req, HTTPResponse res, PublishRequest body) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    String fileName = body == null ? null : body.fileName();

    String url = publishService.createPresignedURL(groupName, fileName);
    res.setStatus(200);
    res.setContentType("application/json");
    res.getWriter().write(new PublishResponse(url).toJSON());
  }
}
