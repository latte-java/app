/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module org.lattejava.app;

import org.lattejava.app.error.Errors;
import org.lattejava.app.service.validation.PublishValidator;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class PublishValidatorTest {
  public PublishValidator validator = new PublishValidator();

  @Test
  public void acceptsKeyWithinNamespace() {
    assertTrue(validator.validate("com.example", "com/example/1.0.0/lib-1.0.0.jar").empty());
  }

  @Test
  public void rejectsBlankFileName() {
    Errors errors = validator.validate("com.example", "  ");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[blank]fileName"));
  }

  @Test
  public void rejectsKeyOutsideNamespace() {
    Errors errors = validator.validate("com.example", "com/other/x.jar");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[outsideNamespace]fileName"));
  }

  @Test
  public void rejectsUncleanKey() {
    Errors errors = validator.validate("com.example", "com/example/../secret.jar");
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("fileName", "[uncleanKey]fileName"));
  }
}
