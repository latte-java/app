/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.Member;
import org.lattejava.app.s3.S3Client;
import org.lattejava.app.s3.S3HttpClient;
import org.lattejava.app.service.validation.*;
import org.lattejava.web.Configuration;

public class GroupService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final DatabaseService databaseService;
  private final S3Client s3Client;
  private final GroupValidator validator;

  public GroupService(Configuration config) {
    this(Services.databaseService(), new GroupValidator(config), new S3HttpClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #GroupService(Configuration)}
   * constructor instead.
   */
  public GroupService(DatabaseService databaseService, GroupValidator validator, S3Client s3Client) {
    this.databaseService = databaseService;
    this.s3Client = s3Client;
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
    databaseService.insertGroup(group);

    if (kind == GroupKind.REVERSE_DNS) {
      databaseService.insertVerification(new GroupVerification(input.name(), now, now));
    }

    Member ownership = new Member(input.name(), creator.userId(), Role.OWNER, MembershipState.ACTIVE, null, null, now);
    databaseService.insertMember(ownership);

    return group;
  }

  /**
   * Deletes {@code group}. Authorization is enforced by the route's
   * {@link org.lattejava.app.security.GroupSecurity} middleware (OWNER required); this method only checks the
   * data-integrity precondition that the group's S3 prefix is empty. On success, the group row is deleted (cascading
   * to members and verifications).
   *
   * @param group The group to delete.
   * @throws ValidationException If the bucket is not empty.
   */
  public void delete(Group group) {
    Errors errors = new Errors();
    String prefix = group.name().replace('.', '/') + "/";
    if (!s3Client.isPrefixEmpty(prefix)) {
      errors.addGeneralError("[hasArtifacts]group",
          "The group [%s] has published artifacts and cannot be deleted.", group.name());
    }
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    databaseService.deleteGroup(group.name());
  }

  public Optional<Group> findGroup(String name) {
    return databaseService.findGroup(name);
  }

  /**
   * Resolves the group that owns {@code namespace} — the most specific registered group that is the namespace itself
   * or an ancestor of it. For a multi-segment namespace the single-segment TLD prefix (e.g. {@code com} in
   * {@code com.example.foo}) is excluded from the candidates, since bare TLDs cannot be registered as groups, so the
   * shallowest candidate is always at least two segments. A single-segment short name is its own sole candidate (it
   * has no ancestors).
   *
   * @param namespace The artifact namespace in dotted form (e.g. {@code com.example.foo}).
   * @return The owning group, or empty if no registered group covers the namespace.
   */
  public Optional<Group> findOwningGroup(String namespace) {
    if (namespace == null || namespace.isBlank()) {
      return Optional.empty();
    }
    String normalized = namespace.trim().toLowerCase(Locale.ROOT);
    String[] segments = normalized.split("\\.");
    List<String> candidates = new ArrayList<>();
    if (segments.length == 1) {
      candidates.add(normalized);
    } else {
      for (int i = segments.length; i >= 2; i--) {
        candidates.add(String.join(".", Arrays.copyOfRange(segments, 0, i)));
      }
    }
    return databaseService.findOwningGroup(candidates);
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
    return databaseService.listGroupsForUser(user.userId(), normalized);
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
    databaseService.updateGroupDescription(group.name(), normalized);
  }
}
