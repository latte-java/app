/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import org.lattejava.app.error.Errors;

/**
 * Validates the {@code id} query parameter of a repository search. Mirrors the two error cases of the former
 * repository-search Worker: a missing {@code id} and an {@code id} that is not a valid Latte artifact identifier
 * ({@code group:project}). The prefix conversion itself lives in
 * {@link org.lattejava.app.service.RepositorySearchService#artifactIdToPrefix}; this only checks the shape.
 */
public class RepositorySearchValidator {
  /**
   * @param id The raw {@code id} query parameter (may be {@code null}).
   * @return The collected errors; empty when the id is present and well-formed.
   */
  public Errors validate(String id) {
    Errors errors = new Errors();
    if (id == null || id.isBlank()) {
      errors.addFieldError("id", "[missing]id", "The [id] parameter is required");
      return errors;
    }

    String[] parts = id.split(":");
    if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      errors.addFieldError("id", "[invalid]id", "The [id] parameter is not a valid artifact ID");
    }

    return errors;
  }
}
