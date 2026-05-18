/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.Member;
import org.lattejava.app.r2.R2Client;
import org.lattejava.app.r2.R2HttpClient;
import org.lattejava.app.service.validation.*;
import org.lattejava.web.Configuration;

public class GroupService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final DatabaseClient databaseClient;
  private final R2Client r2Client;
  private final GroupValidator validator;

  public GroupService(Configuration config) {
    this(config, new GroupValidator(config), new R2HttpClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #GroupService(Configuration)}
   * constructor instead.
   */
  public GroupService(Configuration config, GroupValidator validator, R2Client r2Client) {
    this.databaseClient = new DatabaseClient(config);
    this.r2Client = r2Client;
    this.validator = validator;
  }

  private static String generateVerificationCode() {
    byte[] bytes = new byte[16];
    SECURE_RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /**
   * Creates a new group on behalf of {@code creator}. Only {@code input.name()} and {@code input.description()} are
   * read from the supplied model; {@code state}, {@code verificationCode}, {@code createdAt}, and {@code verifiedAt}
   * are computed by the service based on the group kind (short-name, reverse-DNS, or github.io reverse-DNS).
   *
   * @param input   The user-provided group shape — name and description.
   * @param creator The authenticated user creating the group; becomes the OWNER.
   * @return The persisted group with all server-assigned fields populated.
   * @throws ValidationException If validation fails.
   */
  public Group create(Group input, User creator) {
    Errors errors = validator.validate(input);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    GroupKind kind = GroupValidator.kindOf(input.name());
    Instant now = Instant.now();

    Group group = switch (kind) {
      case REVERSE_DNS -> new Group(input.name(), input.description(), GroupState.PENDING, generateVerificationCode(), now, null);
      case REVERSE_DNS_GITHUB -> new Group(input.name(), input.description(), GroupState.PENDING, null, now, null);
      case SHORT_NAME -> new Group(input.name(), input.description(), GroupState.VERIFIED, null, now, now);
    };
    databaseClient.insertGroup(group);

    if (kind == GroupKind.REVERSE_DNS) {
      databaseClient.insertVerification(new GroupVerification(input.name(), now, now));
    }

    Member ownership = new Member(input.name(), creator.userId(), Role.OWNER, MembershipState.ACTIVE, null, null, now);
    databaseClient.insertMember(ownership);

    return group;
  }

  /**
   * Deletes {@code group} on behalf of {@code current}. The caller must be an active OWNER of the group, and the
   * group's R2 prefix must be empty. On success, the group row is deleted (cascading to members and verifications).
   *
   * @param group   The group to delete.
   * @param current The authenticated user requesting the deletion.
   * @throws ValidationException If the caller is not an OWNER or the bucket is not empty.
   */
  public void delete(Group group, User current) {
    Errors errors = new Errors();
    Optional<Member> membership = databaseClient.findMember(group.name(), current.userId());
    if (membership.isEmpty() || membership.get().role() != Role.OWNER) {
      errors.addGeneralError("[notOwner]group", "Only an OWNER can delete the group [%s].", group.name());
    }
    String prefix = group.name().replace('.', '/') + "/";
    if (errors.empty() && !r2Client.isPrefixEmpty(prefix)) {
      errors.addGeneralError("[hasArtifacts]group",
          "The group [%s] has published artifacts and cannot be deleted.", group.name());
    }
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    databaseClient.deleteGroup(group.name());
  }

  public Optional<Group> findGroup(String name) {
    return databaseClient.findGroup(name);
  }

  public List<Group> listForUser(User user) {
    return listForUser(user, null);
  }

  /**
   * Lists the groups that {@code user} belongs to, optionally narrowing by a case-insensitive substring match
   * against the group name. {@code filter} is trimmed and lowercased before matching; a {@code null} or blank
   * filter returns all of the user's groups.
   *
   * @param user   The viewer whose memberships drive the result.
   * @param filter A substring to match against group names. May be {@code null} or blank.
   * @return The matching groups.
   */
  public List<Group> listForUser(User user, String filter) {
    String normalized = (filter == null || filter.isBlank()) ? null : filter.trim().toLowerCase(Locale.ROOT);
    return databaseClient.listGroupsForUser(user.userId(), normalized);
  }

  /**
   * Updates the description of {@code group}. The description is optional and capped at 500 characters (validated via
   * {@link GroupValidator#validateUpdateDescription(String)}); it is trimmed before persisting, matching the
   * {@link Group} record's normalization.
   *
   * @param group       The group to update. Only its name is used to target the row.
   * @param description The new description. May be {@code null} or blank (cleared to "").
   * @throws ValidationException If the description exceeds the length limit.
   */
  public void updateDescription(Group group, String description) {
    Errors errors = validator.validateUpdateDescription(description);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    String normalized = description == null ? "" : description.trim();
    databaseClient.updateGroupDescription(group.name(), normalized);
  }
}
