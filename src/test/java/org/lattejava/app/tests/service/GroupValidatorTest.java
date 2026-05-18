/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class GroupValidatorTest {
  public DatabaseClient client;
  public GroupValidator validator;

  private static Group group(String name) {
    return new Group(name, "", GroupState.PENDING, null, Instant.EPOCH, null);
  }

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("d1.accountId", "d1.apiToken", "d1.baseUrl", "d1.databaseId"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new DatabaseClient(config);
    TLDList tlds = new TLDList(Set.of("org", "com", "io", "dev", "net"));
    validator = new GroupValidator(client, tlds);
  }

  @Test
  public void acceptsSegmentWithDashes() {
    assertTrue(validator.validate(group("io.github.latte-java")).empty());
  }

  @Test
  public void acceptsSegmentWithLeadingDigit() {
    assertTrue(validator.validate(group("io.42-labs")).empty());
  }

  @Test
  public void acceptsShortName() {
    assertTrue(validator.validate(group("my-project")).empty());
  }

  @Test
  public void acceptsShortNameSingleChar() {
    assertTrue(validator.validate(group("x")).empty());
  }

  @Test
  public void kindOfGitHub() {
    assertEquals(GroupValidator.kindOf("io.github.someone"), GroupKind.REVERSE_DNS_GITHUB);
  }

  @Test
  public void kindOfReverseDNS() {
    assertEquals(GroupValidator.kindOf("org.example"), GroupKind.REVERSE_DNS);
  }

  @Test
  public void kindOfShortName() {
    assertEquals(GroupValidator.kindOf("my-project"), GroupKind.SHORT_NAME);
  }

  @Test
  public void rejectsAncestorPrefix() {
    Group parent = new Group("test.parentcheck", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(parent);
    try {
      Errors errors = validator.validate(group("test.parentcheck.child"));
      assertFalse(errors.empty());
      assertNotNull(errors.getFieldError("name", "[ancestorConflict]name"));
    } finally {
      client.deleteGroup("test.parentcheck");
    }
  }

  @Test
  public void rejectsConsecutiveDots() {
    Errors errors = validator.validate(group("org..example"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[emptySegment]name"));
  }

  @Test
  public void rejectsExistingName() {
    Group existing = new Group("test.exists.fixture", "", GroupState.VERIFIED, null, Instant.ofEpochMilli(1714867200000L), Instant.ofEpochMilli(1714867200000L));
    client.insertGroup(existing);
    try {
      Errors errors = validator.validate(group("test.exists.fixture"));
      assertFalse(errors.empty());
      assertNotNull(errors.getFieldError("name", "[duplicate]name"));
    } finally {
      client.deleteGroup("test.exists.fixture");
    }
  }

  @Test
  public void rejectsForwardDNS() {
    Errors errors = validator.validate(group("example.org"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[unknownTld]name"));
  }

  @Test
  public void rejectsLeadingDot() {
    Errors errors = validator.validate(group(".org.example"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[emptySegment]name"));
  }

  @Test
  public void rejectsNonAscii() {
    Errors errors = validator.validate(group("éclair"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[notAscii]name"));
  }

  @Test
  public void rejectsSegmentWithLeadingDash() {
    Errors errors = validator.validate(group("org.-example"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[invalidSegment]name"));
  }

  @Test
  public void rejectsSegmentWithUnderscore() {
    Errors errors = validator.validate(group("org.foo_bar"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[invalidSegment]name"));
  }

  @Test
  public void rejectsSegmentTooLong() {
    Errors errors = validator.validate(group("org." + "a".repeat(64)));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[segmentTooLong]name"));
  }

  @Test
  public void rejectsShortNameWithUnderscore() {
    Errors errors = validator.validate(group("my_project"));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[invalidSegment]name"));
  }

  @Test
  public void rejectsTooLong() {
    Errors errors = validator.validate(group("org." + "a".repeat(250)));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("name", "[tooLong]name"));
  }

  @Test
  public void updateDescription_acceptsAtLimit() {
    assertTrue(validator.validateUpdateDescription("x".repeat(500)).empty());
  }

  @Test
  public void updateDescription_acceptsEmpty() {
    assertTrue(validator.validateUpdateDescription("").empty());
    assertTrue(validator.validateUpdateDescription(null).empty());
  }

  @Test
  public void updateDescription_rejectsTooLong() {
    Errors errors = validator.validateUpdateDescription("x".repeat(501));
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("description", "[tooLong]description"));
  }

  @Test
  public void updateDescription_trimsBeforeMeasuring() {
    assertTrue(validator.validateUpdateDescription("  " + "x".repeat(500) + "  ").empty());
  }

  @Test
  public void validate_rejectsTooLongDescriptionOnCreate() {
    Group g = new Group("io.github.toolongdesc", "x".repeat(501), GroupState.PENDING, null, Instant.EPOCH, null);
    Errors errors = validator.validate(g);
    assertFalse(errors.empty());
    assertNotNull(errors.getFieldError("description", "[tooLong]description"));
  }

  @Test
  public void validReverseDNS() {
    assertTrue(validator.validate(group("org.example")).empty());
  }
}
