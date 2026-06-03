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
import org.lattejava.app.model.Group;
import org.lattejava.app.model.User;
import org.lattejava.web.Configuration;

public class VerificationService {
  public static final Duration DEADLINE = Duration.ofHours(48);
  private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
  private static final UUID GITHUB_IDP_ID = UUID.fromString("18cc6f6e-5208-48b1-a2b3-dd55c30733de");
  private static final String GITHUB_SCOPE = "read:user user:email read:org";
  private static final System.Logger LOG = System.getLogger(VerificationService.class.getName());
  private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "verification-scanner");
    t.setDaemon(true);
    return t;
  });

  private final DatabaseService databaseService;
  private final DNSResolver dnsResolver;
  private final FusionAuthClient fusionAuth;
  private final GitHubClient githubClient;
  private final String githubClientId;
  private final String githubClientSecret;

  public VerificationService(Configuration config) {
    this(Services.databaseService(), config, new JNDIDNSResolver(), new GitHubHTTPClient());
  }

  /**
   * Test-only constructor. Production code should use the {@link #VerificationService(Configuration)} constructor
   * instead.
   */
  public VerificationService(DatabaseService databaseService, Configuration config, DNSResolver dnsResolver, GitHubClient githubClient) {
    this.databaseService = databaseService;
    this.dnsResolver = dnsResolver;
    this.fusionAuth = new FusionAuthClient(
        config.get("fusionauth.apiKey"),
        config.get("fusionauth.baseUrl")
    );
    this.githubClient = githubClient;
    this.githubClientId = config.get("github.clientId");
    this.githubClientSecret = config.get("github.clientSecret");
  }

  public String buildGitHubAuthorizeURL(String state, String redirectURI) {
    return GITHUB_AUTHORIZE_URL
        + "?client_id=" + URLEncoder.encode(githubClientId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectURI, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode(GITHUB_SCOPE, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
  }

  /**
   * Runs a verification check for the given group, recovering a {@code FAILED} group along the way. If the group is
   * {@code FAILED}, it is reset to {@code PENDING} with a fresh {@link GroupVerification} (resetting the deadline)
   * before the DNS check runs. Otherwise the existing outstanding verification, if any, is checked. Missing,
   * {@code VERIFIED}, and {@code PENDING}-without-a-record groups are no-ops.
   *
   * @param groupName the group to check.
   * @param now       the current instant.
   */
  public void check(String groupName, Instant now) {
    Optional<Group> groupOpt = databaseService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      return;
    }

    if (groupOpt.get().state() == GroupState.FAILED) {
      databaseService.updateGroupState(groupName, GroupState.PENDING, null);
      GroupVerification verification = new GroupVerification(groupName, now, now);
      databaseService.insertVerification(verification);
      checkOne(verification, now);
      return;
    }

    databaseService.findVerification(groupName).ifPresent(v -> checkOne(v, now));
  }

  public void checkOne(GroupVerification verification, Instant now) {
    Optional<Group> groupOpt = databaseService.findGroup(verification.groupName());
    if (groupOpt.isEmpty()) {
      databaseService.deleteVerification(verification.groupName());
      return;
    }

    Group group = groupOpt.get();
    String code = group.verificationCode();
    if (code == null) {
      databaseService.deleteVerification(verification.groupName());
      return;
    }

    String host = VerificationView.forwardDomain(verification.groupName());
    List<String> txtRecords;
    try {
      txtRecords = dnsResolver.lookupTXT(host);
    } catch (RuntimeException e) {
      txtRecords = List.of();
    }

    boolean matched = txtRecords.stream().anyMatch(code::equals);
    if (matched) {
      databaseService.updateGroupState(verification.groupName(), GroupState.VERIFIED, now);
      databaseService.deleteVerification(verification.groupName());
      return;
    }

    Instant deadline = verification.startedAt().plus(DEADLINE);
    if (!now.isBefore(deadline)) {
      databaseService.updateGroupState(verification.groupName(), GroupState.FAILED, null);
      databaseService.deleteVerification(verification.groupName());
      return;
    }

    databaseService.updateVerificationLastChecked(verification.groupName(), now);
  }

  public Optional<GroupVerification> findVerification(String groupName) {
    return databaseService.findVerification(groupName);
  }

  public boolean hasGitHubLink(UUID userId) {
    IdentityProviderLink link = retrieveGitHubLink(userId);
    return link != null && link.token != null;
  }

  public GitHubLinkResult linkGitHub(UUID userId, String code, String redirectURI) {
    String token = githubClient.exchangeCodeForToken(githubClientId, githubClientSecret, code, redirectURI);
    if (token == null) {
      return GitHubLinkResult.OAUTH_FAILED;
    }

    GitHubUser githubUser = githubClient.getUser(token);
    if (githubUser == null) {
      return GitHubLinkResult.OAUTH_FAILED;
    }

    IdentityProviderLink existing = retrieveGitHubLink(userId);
    if (existing != null) {
      unlinkGitHub(userId, existing);
    }

    IdentityProviderLink link = new IdentityProviderLink();
    link.identityProviderId = GITHUB_IDP_ID;
    link.identityProviderUserId = Long.toString(githubUser.id());
    link.userId = userId;
    link.token = token;
    link.displayName = githubUser.login();

    IdentityProviderLinkRequest request = new IdentityProviderLinkRequest();
    request.identityProviderLink = link;

    ClientResponse<IdentityProviderLinkResponse, ?> response = fusionAuth.createUserLink(request);
    if (!response.wasSuccessful()) {
      LOG.log(System.Logger.Level.ERROR,
          "Failed to link GitHub account [" + githubUser.login() + "] to user [" + userId + "]. FA status code [" + response.status + "]. FA error [" + response.errorResponse + "]");
      return GitHubLinkResult.LINK_FAILED;
    }

    return GitHubLinkResult.LINKED;
  }

  public void scan(Instant now) {
    List<GroupVerification> due = databaseService.listVerificationsDueForCheck(now);
    for (GroupVerification v : due) {
      try {
        checkOne(v, now);
      } catch (RuntimeException e) {
        // Per-verification failures must not abort the loop.
      }
    }
  }

  public void shutdown() {
    scheduler.shutdownNow();
  }

  public void start() {
    long period = Duration.ofMinutes(3).toMillis();
    scheduler.scheduleAtFixedRate(
        () -> scan(Instant.now()),
        period,
        period,
        TimeUnit.MILLISECONDS
    );
  }

  public GitHubVerifyResult verifyGitHub(Group group, User current) {
    if (GroupValidator.kindOf(group.name()) != GroupKind.REVERSE_DNS_GITHUB) {
      Errors errors = new Errors();
      errors.addGeneralError("[notGitHub]group", "The group [%s] is not a github.io group.", group.name());
      throw new ValidationException(errors);
    }

    IdentityProviderLink link = retrieveGitHubLink(current.userId());
    if (link == null || link.token == null) {
      return GitHubVerifyResult.NOT_LINKED;
    }

    String[] segments = group.name().split("\\.");
    String accountOrOrg = segments[2];

    String login = githubClient.getLogin(link.token);
    if (login == null) {
      unlinkGitHub(current.userId(), link);
      return GitHubVerifyResult.UNAUTHORIZED;
    }

    AccountType accountType = githubClient.getAccountType(link.token, accountOrOrg);
    if (accountType == AccountType.UNAUTHORIZED) {
      unlinkGitHub(current.userId(), link);
      return GitHubVerifyResult.UNAUTHORIZED;
    }

    if (accountType == AccountType.NOT_FOUND) {
      return GitHubVerifyResult.NOT_AUTHORIZED;
    }

    if (accountType == AccountType.USER) {
      if (!login.equalsIgnoreCase(accountOrOrg)) {
        return GitHubVerifyResult.NOT_AUTHORIZED;
      }
    } else {
      MembershipStatus status = githubClient.checkOrgMembership(link.token, accountOrOrg, login);
      if (status == MembershipStatus.UNAUTHORIZED) {
        unlinkGitHub(current.userId(), link);
        return GitHubVerifyResult.UNAUTHORIZED;
      }
      if (status != MembershipStatus.MEMBER) {
        return GitHubVerifyResult.NOT_AUTHORIZED;
      }
    }

    databaseService.updateGroupState(group.name(), GroupState.VERIFIED, Instant.now());
    databaseService.deleteVerification(group.name());
    return GitHubVerifyResult.VERIFIED;
  }

  private IdentityProviderLink retrieveGitHubLink(UUID userId) {
    ClientResponse<IdentityProviderLinkResponse, ?> response = fusionAuth.retrieveUserLinksByUserId(GITHUB_IDP_ID, userId);
    if (!response.wasSuccessful() ||
        response.successResponse == null ||
        response.successResponse.identityProviderLinks == null ||
        response.successResponse.identityProviderLinks.isEmpty()) {
      return null;
    }
    return response.successResponse.identityProviderLinks.getFirst();
  }

  private void unlinkGitHub(UUID userId, IdentityProviderLink link) {
    fusionAuth.deleteUserLink(GITHUB_IDP_ID, link.identityProviderUserId, userId);
  }
}
