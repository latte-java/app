/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module org.lattejava.app;
import module org.testng;

import org.lattejava.app.error.Errors;
import org.lattejava.app.service.validation.RepositorySearchValidator;

import static org.testng.Assert.*;

@Test
public class RepositorySearchValidatorTest {
  private final RepositorySearchValidator validator = new RepositorySearchValidator();

  @Test
  public void validate_validId_noErrors() {
    assertTrue(validator.validate("org.lattejava.plugin:dependency").empty());
  }

  @Test
  public void validate_nullId_missingError() {
    Errors errors = validator.validate(null);
    assertNotNull(errors.getFieldError("id", "[missing]id"));
  }

  @Test
  public void validate_blankId_missingError() {
    Errors errors = validator.validate("  ");
    assertNotNull(errors.getFieldError("id", "[missing]id"));
  }

  @Test
  public void validate_noColon_invalidError() {
    Errors errors = validator.validate("invalid");
    assertNotNull(errors.getFieldError("id", "[invalid]id"));
  }

  @Test
  public void validate_emptyProject_invalidError() {
    Errors errors = validator.validate("org.lattejava:");
    assertNotNull(errors.getFieldError("id", "[invalid]id"));
  }
}
