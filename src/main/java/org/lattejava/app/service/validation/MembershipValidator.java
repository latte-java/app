/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.validation;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.model.Member;
import org.lattejava.web.*;

public class MembershipValidator {
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final DatabaseClient databaseClient;

  public MembershipValidator(Configuration config) {
    this(new DatabaseClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #MembershipValidator(Configuration)} constructor
   * instead.
   */
  public MembershipValidator(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Errors validateChangeRole(String groupName, UUID targetUserId, Role newRole, User current) {
    Errors errors = new Errors();
    if (current.userId().equals(targetUserId)) {
      errors.addGeneralError("[selfRoleChange]current", "You cannot change your own role.");
      return errors;
    }

    Optional<Member> target = databaseClient.findMember(groupName, targetUserId);
    if (target.isEmpty()) {
      return errors;
    }

    if (target.get().role() == Role.OWNER && target.get().state() == MembershipState.ACTIVE && newRole != Role.OWNER) {
      List<Member> owners = databaseClient.findActiveOwners(groupName);
      if (owners.size() <= 1) {
        errors.addGeneralError("[lastOwner]group", "Cannot demote the last active OWNER of the group [%s].", groupName);
      }
    }

    return errors;
  }

  public Errors validateInvite(InviteRequest request) {
    Errors errors = new Errors();
    if (request == null) {
      errors.addGeneralError("[null]request", "An invite request is required.");
      return errors;
    }

    if (request.groupName() == null || request.groupName().isBlank()) {
      errors.addGeneralError("[blank]groupName", "A group name is required.");
    }

    String email = request.email() == null ? "" : request.email().toLowerCase(Locale.ROOT).trim();
    if (email.isEmpty()) {
      errors.addFieldError("email", "[blank]email", "An email address is required.");
    } else if (!EMAIL_PATTERN.matcher(email).matches()) {
      errors.addFieldError("email", "[notValid]email", "The email address [%s] is not a valid format.", email);
    }

    if (request.role() == null) {
      errors.addFieldError("role", "[blank]role", "A role is required.");
    }

    return errors;
  }

  public Errors validateLeave(String groupName, User current) {
    Errors errors = new Errors();
    Optional<Member> member = databaseClient.findMember(groupName, current.userId());
    if (member.isEmpty()) {
      return errors;
    }

    if (member.get().role() == Role.OWNER && member.get().state() == MembershipState.ACTIVE) {
      List<Member> owners = databaseClient.findActiveOwners(groupName);
      if (owners.size() <= 1) {
        errors.addGeneralError("[lastOwner]group", "Cannot leave the group [%s] as the last active OWNER.", groupName);
      }
    }

    return errors;
  }

  public Errors validateNoDuplicateMembership(String groupName, UUID userId, String email) {
    Errors errors = new Errors();
    if (databaseClient.findMember(groupName, userId).isPresent()) {
      errors.addFieldError("email", "[alreadyInvited]email", "[%s] is already a member or has a pending invitation.", email);
    }

    return errors;
  }

  public Errors validateRemove(String groupName, UUID targetUserId, User current) {
    Errors errors = new Errors();
    if (current.userId().equals(targetUserId)) {
      errors.addGeneralError("[selfRemove]current", "You cannot remove yourself from the group. Use Leave instead.");
      return errors;
    }

    Optional<Member> target = databaseClient.findMember(groupName, targetUserId);
    if (target.isEmpty()) {
      return errors;
    }

    if (target.get().role() == Role.OWNER && target.get().state() == MembershipState.ACTIVE) {
      List<Member> owners = databaseClient.findActiveOwners(groupName);
      if (owners.size() <= 1) {
        errors.addGeneralError("[lastOwner]group", "Cannot remove the last active OWNER of the group [%s].", groupName);
      }
    }

    return errors;
  }
}
