/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.Configuration;

public class GroupValidator {
  private static final Pattern SEGMENT_PATTERN = Pattern.compile("[a-z0-9]([a-z0-9-]*[a-z0-9])?");
  private final DatabaseClient databaseClient;
  private final TLDList tlds;

  public GroupValidator(Configuration config) {
    this(new DatabaseClient(config), TLDList.fromIana());
  }

  /**
   * Test-only constructor. Production code should use the {@link #GroupValidator(Configuration)}
   * constructor instead.
   */
  public GroupValidator(DatabaseClient databaseClient, TLDList tlds) {
    this.databaseClient = databaseClient;
    this.tlds = tlds;
  }

  public static GroupKind kindOf(String name) {
    String[] segments = name.split("\\.");
    if (segments.length == 1) {
      return GroupKind.SHORT_NAME;
    }
    if (segments.length >= 3 && "io".equals(segments[0]) && "github".equals(segments[1])) {
      return GroupKind.REVERSE_DNS_GITHUB;
    }
    return GroupKind.REVERSE_DNS;
  }

  public Errors validate(Group group) {
    Errors errors = new Errors();
    if (group == null) {
      errors.addGeneralError("[null]group", "A group is required.");
      return errors;
    }

    validateDescription(errors, group.description());

    String name = group.name();
    if (name == null || name.isBlank()) {
      errors.addFieldError("name", "[blank]name", "A group name is required.");
      return errors;
    }
    if (!name.chars().allMatch(c -> c < 0x80)) {
      errors.addFieldError("name", "[notAscii]name",
          "The group name [%s] contains non-ASCII characters. Use ASCII letters, digits, hyphens, and dots only.", name);
    }
    if (name.length() > 253) {
      errors.addFieldError("name", "[tooLong]name",
          "The group name is [%d] characters long. Group names must be 253 characters or fewer.", name.length());
    }

    String[] segments = name.split("\\.", -1);
    boolean structureValid = true;

    for (String segment : segments) {
      if (segment.isEmpty()) {
        errors.addFieldError("name", "[emptySegment]name",
            "The group name [%s] has an empty segment. Check for consecutive dots or a leading or trailing dot.", name);
        structureValid = false;
        break;
      }
      if (segment.length() > 63) {
        errors.addFieldError("name", "[segmentTooLong]name",
            "The segment [%s] is [%d] characters long. DNS labels must be 63 characters or fewer.", segment, segment.length());
        structureValid = false;
      }
      if (!SEGMENT_PATTERN.matcher(segment).matches()) {
        errors.addFieldError("name", "[invalidSegment]name",
            "The segment [%s] is not a valid DNS label. Each segment must start and end with a letter or digit and contain only letters, digits, or hyphens.", segment);
        structureValid = false;
      }
    }

    if (structureValid && segments.length > 1) {
      String firstSegment = segments[0];
      if (!tlds.contains(firstSegment)) {
        errors.addFieldError("name", "[unknownTld]name",
            "The group name [%s] starts with [%s], which is not a recognized top-level domain (TLD). Reverse-DNS group names must start with a real TLD such as [com], [org], or [io].", name, firstSegment);
      }
    }

    if (structureValid && segments.length == 1 && tlds.contains(name)) {
      errors.addFieldError("name", "[tld]name",
          "The group name [%s] is a top-level domain (TLD). Short group names cannot be bare TLDs; use a reverse-DNS name such as [%s.yourorg] instead.", name, name);
    }

    if (structureValid && databaseClient.findGroup(name).isPresent()) {
      errors.addFieldError("name", "[duplicate]name", "A group named [%s] already exists.", name);
    }

    if (structureValid && segments.length > 1) {
      Optional<Group> ancestor = databaseClient.findAncestorGroup(name);
      ancestor.ifPresent(value -> errors.addFieldError("name", "[ancestorConflict]name",
          "The existing group [%s] already covers [%s]. Subgroups are implicit and cannot be created separately.", value.name(), name));
    }

    return errors;
  }

  public Errors validateUpdateDescription(String description) {
    Errors errors = new Errors();
    validateDescription(errors, description);
    return errors;
  }

  private void validateDescription(Errors errors, String description) {
    String trimmed = description == null ? "" : description.trim();
    if (trimmed.length() > 500) {
      errors.addFieldError("description", "[tooLong]description",
          "The description is [%d] characters long. Descriptions must be 500 characters or fewer.",
          trimmed.length());
    }
    // Empty/blank is valid (optional field). No other checks.
  }
}
