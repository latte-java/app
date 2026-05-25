/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import org.lattejava.web.*;

/**
 * A simple service registry.
 */
public class Services {
  private static GroupService groupService;
  private static MembershipService membershipService;
  private static PublishService publishService;
  private static VerificationService verificationService;
  private static ViewService viewService;

  public static GroupService groupService() {
    return groupService;
  }

  public static void initialize(Configuration config) {
    groupService = new GroupService(config);
    membershipService = new MembershipService(config);
    publishService = new PublishService(config);
    verificationService = new VerificationService(config);
    viewService = new ViewService(config);

    // Kick off the verification scheduled task
    verificationService.start();
  }

  public static MembershipService membershipService() {
    return membershipService;
  }

  public static PublishService publishService() {
    return publishService;
  }

  public static void shutdown() {
    verificationService.shutdown();
  }

  public static VerificationService verificationService() {
    return verificationService;
  }

  public static ViewService viewService() {
    return viewService;
  }
}
