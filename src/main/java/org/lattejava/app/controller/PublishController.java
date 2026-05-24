/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module com.fasterxml.jackson.databind;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;

import org.lattejava.app.model.PublishRequest;
import org.lattejava.app.model.PublishResponse;
import org.lattejava.app.service.PublishService;
import org.lattejava.app.service.Services;

/**
 * Handles {@code POST /api/v1/publish/{groupName}}: asks {@link PublishService} for a presigned PUT URL and returns it
 * as JSON. The request body is parsed into a {@link PublishRequest} by a {@code JSONBodySupplier} on the route, so this
 * is a {@link org.lattejava.web.BodyHandler}. Authentication and group authorization run upstream as middleware (see
 * {@link org.lattejava.app.security.PublishAuthorizer}). Error responses are rendered by the {@code /api}
 * {@link org.lattejava.app.middleware.APIExceptionHandler}: a malformed body throws
 * {@link org.lattejava.web.BadRequestException} from the supplier, and validation/presign failures throw exceptions
 * this method lets propagate, so the happy path is all that lives here.
 *
 * @author Brian Pontarelli
 */
public class PublishController {
  private static final String GROUP_NAME = "groupName";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final PublishService publishService;

  public PublishController() {
    this.publishService = Services.publishService();
  }

  public void publish(HTTPRequest req, HTTPResponse res, PublishRequest body) throws IOException {
    String groupName = (String) req.getAttribute(GROUP_NAME);
    String fileName = body == null ? null : body.fileName();

    String url = publishService.createPresignedURL(groupName, fileName);
    res.setStatus(200);
    res.setContentType("application/json");
    MAPPER.writeValue(res.getOutputStream(), new PublishResponse(url));
  }
}
