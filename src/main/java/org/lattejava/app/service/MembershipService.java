/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module restify;

import org.lattejava.app.model.Member;
import org.lattejava.app.model.User;
import org.lattejava.web.*;

public class MembershipService {
  private static final UUID APPLICATION_ID = UUID.fromString("e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  private static final UUID INVITE_TEMPLATE_ID = UUID.fromString("a4db4962-efd8-476d-af15-932567f337b8");
  private static final System.Logger LOG = System.getLogger(MembershipService.class.getName());
  private final DatabaseClient databaseClient;
  private final FusionAuthClient fusionAuth;
  private final MembershipValidator validator;

  public MembershipService(Configuration config) {
    this.databaseClient = new DatabaseClient(config);
    this.fusionAuth = new FusionAuthClient(
        config.get("fusionauth.apiKey"),
        config.get("fusionauth.baseUrl")
    );
    this.validator = new MembershipValidator(config);
  }

  public void acceptInvitation(String groupName, UUID userId) {
    Optional<Member> member = databaseClient.findMember(groupName, userId);
    if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
      return;
    }

    databaseClient.updateMemberState(groupName, userId, MembershipState.ACTIVE, Instant.now());
  }

  public void changeRole(String groupName, UUID targetUserId, Role newRole, User current) {
    Errors errors = validator.validateChangeRole(groupName, targetUserId, newRole, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseClient.updateMemberRole(groupName, targetUserId, newRole);
  }

  public void declineInvitation(String groupName, UUID userId) {
    Optional<Member> member = databaseClient.findMember(groupName, userId);
    if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
      return;
    }

    databaseClient.deleteMember(groupName, userId);
  }

  public Optional<Member> findMember(String groupName, UUID userId) {
    return databaseClient.findMember(groupName, userId);
  }

  public Member invite(InviteRequest request, User inviter) {
    Errors errors = validator.validateInvite(request);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    String email = request.email();
    UUID userId;
    ClientResponse<UserResponse, ?> lookup = fusionAuth.retrieveUserByEmail(email);
    if (lookup.wasSuccessful() && lookup.successResponse != null && lookup.successResponse.user != null) {
      userId = lookup.successResponse.user.id;

      // This is only possible for existing users
      Errors dupErrors = validator.validateNoDuplicateMembership(request.groupName(), userId, email);
      if (!dupErrors.empty()) {
        throw new ValidationException(dupErrors);
      }

      var sendRequest = new SendRequest(List.of(userId), Map.of("groupName", request.groupName()));
      ClientResponse<SendResponse, ?> sendResponse = fusionAuth.sendEmail(INVITE_TEMPLATE_ID, sendRequest);
      if (!sendResponse.wasSuccessful()) {
        LOG.log(System.Logger.Level.WARNING,
            "Failed to send invite email to [" + email + "] for group [" + request.groupName() + "]. FA error [" + sendResponse.errorResponse + "]");
      }
    } else {
      userId = UUID.randomUUID();
      var newUser = new io.fusionauth.domain.User().with(u -> u.id = userId)
                                                   .with(u -> u.email = email);
      var registration = new UserRegistration().with(ur -> ur.applicationId = APPLICATION_ID);
      var registrationRequest = new RegistrationRequest(newUser, registration);
      registrationRequest.sendSetPasswordIdentityType = SendSetPasswordIdentityType.email;

      ClientResponse<RegistrationResponse, ?> createResponse = fusionAuth.register(userId, registrationRequest);
      if (!createResponse.wasSuccessful()) {
        throw new IllegalStateException("Failed to create FusionAuth user for [" + email + "]. FA error [" + createResponse.errorResponse.toString() + "]");
      }
    }

    Instant now = Instant.now();
    Member member = new Member(
        request.groupName(),
        userId,
        request.role(),
        MembershipState.PENDING,
        inviter.userId(),
        now,
        null
    );
    databaseClient.insertMember(member);
    return member;
  }

  public void leave(String groupName, User current) {
    Errors errors = validator.validateLeave(groupName, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseClient.deleteMember(groupName, current.userId());
  }

  public List<Member> listMembers(String groupName) {
    return databaseClient.listMembers(groupName);
  }

  public void remove(String groupName, UUID targetUserId, User current) {
    Errors errors = validator.validateRemove(groupName, targetUserId, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseClient.deleteMember(groupName, targetUserId);
  }
}
