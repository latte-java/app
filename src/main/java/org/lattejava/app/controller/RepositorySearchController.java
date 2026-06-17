/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;

import org.lattejava.app.model.RepositorySearchResponse;
import org.lattejava.app.service.RepositorySearchService;
import org.lattejava.app.service.Services;

/**
 * Handles {@code GET /api/v1/repository/search?id=<artifactId>&latest=<bool>}: looks up an artifact's versions in the
 * repository bucket and returns them as JSON. This is the public, unauthenticated port of the former Cloudflare
 * {@code repository-search} Worker. A missing/invalid {@code id} surfaces as a {@code 400} (the {@code Errors} JSON,
 * rendered by the {@code /api} {@link org.lattejava.app.middleware.APIExceptionHandler}); an artifact with no versions
 * is a {@code 404} with an empty body; otherwise the versions are returned newest first (or just the newest when
 * {@code latest=true}).
 *
 * @author Brian Pontarelli
 */
public class RepositorySearchController {
  private final RepositorySearchService repositorySearchService;

  public RepositorySearchController() {
    this.repositorySearchService = Services.repositorySearchService();
  }

  public void search(HTTPRequest req, HTTPResponse res) throws IOException {
    String id = req.getURLParameter("id");
    boolean latest = "true".equals(req.getURLParameter("latest"));

    Optional<RepositorySearchResponse> result = repositorySearchService.search(id, latest);
    if (result.isEmpty()) {
      res.setStatus(404);
      return;
    }

    res.setStatus(200);
    res.setContentType("application/json");
    res.getWriter().write(result.get().toJSON());
  }
}
