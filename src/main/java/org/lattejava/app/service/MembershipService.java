/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module fusionauth.java.client;
import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;

import com.inversoft.rest.*;
import org.lattejava.app.model.Member;
import org.lattejava.app.model.User;
import org.lattejava.web.Configuration;

public class MembershipService {
  private static final UUID APPLICATION_ID = UUID.fromString("e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
  private static final UUID INVITE_TEMPLATE_ID = UUID.fromString("a4db4962-efd8-476d-af15-932567f337b8");
  private static final System.Logger LOG = System.getLogger(MembershipService.class.getName());
  private final DatabaseService databaseService;
  private final FusionAuthClient fusionAuth;
  private final MembershipValidator validator;

  public MembershipService(Configuration config) {
    this(Services.databaseService(), config);
  }

  /**
   * Test-only constructor. Production code should use the {@link #MembershipService(Configuration)}
   * constructor instead.
   */
  public MembershipService(DatabaseService databaseService, Configuration config) {
    this.databaseService = databaseService;
    this.fusionAuth = new FusionAuthClient(
        config.get("fusionauth.apiKey"),
        config.get("fusionauth.baseUrl")
    );
    this.validator = new MembershipValidator(databaseService);
  }

  public void acceptInvitation(String groupName, UUID userId) {
    Optional<Member> member = databaseService.findMember(groupName, userId);
    if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
      return;
    }

    databaseService.updateMemberState(groupName, userId, MembershipState.ACTIVE, Instant.now());
  }

  public void changeRole(String groupName, UUID targetUserId, Role newRole, User current) {
    Errors errors = validator.validateChangeRole(groupName, targetUserId, newRole, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseService.updateMemberRole(groupName, targetUserId, newRole);
  }

  public void declineInvitation(String groupName, UUID userId) {
    Optional<Member> member = databaseService.findMember(groupName, userId);
    if (member.isEmpty() || member.get().state() != MembershipState.PENDING) {
      return;
    }

    databaseService.deleteMember(groupName, userId);
  }

  /**
   * Like {@link #findMember(String, UUID)}, but enriches the returned {@link Member#user()} from FusionAuth so callers
   * that need email/username (member-detail forms, role/remove confirmation pages) get a single-row D1 read plus a
   * single FA {@code retrieveUser} call rather than re-fetching every member of the group via {@link #listMembers}.
   *
   * @param groupName The group.
   * @param userId    The FusionAuth user UUID of the member.
   * @return The enriched member, or empty if no row exists.
   */
  public Optional<Member> findEnrichedMember(String groupName, UUID userId) {
    Optional<Member> memberOpt = databaseService.findMember(groupName, userId);
    if (memberOpt.isEmpty()) {
      return memberOpt;
    }

    Member m = memberOpt.get();
    ClientResponse<UserResponse, ?> response = fusionAuth.retrieveUser(userId);
    User user;
    if (response.wasSuccessful() && response.successResponse != null && response.successResponse.user != null) {
      user = UserService.toUser(response.successResponse.user);
    } else {
      LOG.log(System.Logger.Level.WARNING,
          "FusionAuth has no user for member [" + m.userId() + "] in group [" + m.groupName() + "]");
      user = m.user();
    }
    return Optional.of(new Member(m.groupName(), user, m.role(), m.state(), m.invitedBy(), m.invitedAt(), m.joinedAt()));
  }

  public Optional<Member> findMember(String groupName, UUID userId) {
    return databaseService.findMember(groupName, userId);
  }

  public Member invite(InviteRequest request, User inviter) {
    Errors errors = validator.validateInvite(request);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    String email = request.email();
    UUID userId;
    User invitedUser;
    ClientResponse<UserResponse, ?> lookup = fusionAuth.retrieveUserByEmail(email);
    if (lookup.wasSuccessful() && lookup.successResponse != null && lookup.successResponse.user != null) {
      userId = lookup.successResponse.user.id;
      invitedUser = UserService.toUser(lookup.successResponse.user);

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
      invitedUser = new User(userId, email, null);
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
        invitedUser,
        request.role(),
        MembershipState.PENDING,
        inviter.userId(),
        now,
        null
    );
    databaseService.insertMember(member);
    return member;
  }

  public void leave(String groupName, User current) {
    Errors errors = validator.validateLeave(groupName, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseService.deleteMember(groupName, current.userId());
  }

  public List<Member> listMembers(String groupName) {
    List<Member> members = databaseService.listMembers(groupName);
    if (members.isEmpty()) {
      return members;
    }

    List<UUID> ids = members.stream().map(Member::userId).toList();
    ClientResponse<SearchResponse, ?> response = fusionAuth.searchUsersByIds(ids);
    Map<UUID, io.fusionauth.domain.User> byId = new HashMap<>();
    if (response.wasSuccessful() && response.successResponse != null && response.successResponse.users != null) {
      for (io.fusionauth.domain.User faUser : response.successResponse.users) {
        byId.put(faUser.id, faUser);
      }
    }

    List<Member> enriched = new ArrayList<>(members.size());
    for (Member m : members) {
      io.fusionauth.domain.User faUser = byId.get(m.userId());
      User user;
      if (faUser == null) {
        LOG.log(System.Logger.Level.WARNING,
            "FusionAuth has no user for member [" + m.userId() + "] in group [" + m.groupName() + "]");
        user = m.user();
      } else {
        user = UserService.toUser(faUser);
      }
      enriched.add(new Member(m.groupName(), user, m.role(), m.state(), m.invitedBy(), m.invitedAt(), m.joinedAt()));
    }
    return enriched;
  }

  public void remove(String groupName, UUID targetUserId, User current) {
    Errors errors = validator.validateRemove(groupName, targetUserId, current);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }

    databaseService.deleteMember(groupName, targetUserId);
  }
}
