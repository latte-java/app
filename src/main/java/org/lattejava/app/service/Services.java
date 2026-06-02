/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module org.lattejava.web;

import org.lattejava.app.db.DatabaseService;

/**
 * A simple service registry.
 */
public class Services {
  private static DatabaseService databaseService;
  private static GroupService groupService;
  private static MembershipService membershipService;
  private static PublishService publishService;
  private static RepositorySearchService repositorySearchService;
  private static VerificationService verificationService;
  private static ViewService viewService;

  public static DatabaseService databaseService() {
    return databaseService;
  }

  public static GroupService groupService() {
    return groupService;
  }

  public static void initialize(Configuration config) {
    // The database service owns the connection pool + jOOQ context and must exist before any service that uses it.
    databaseService = new DatabaseService(config);
    groupService = new GroupService(config);
    membershipService = new MembershipService(config);
    publishService = new PublishService(config);
    repositorySearchService = new RepositorySearchService(config);
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

  public static RepositorySearchService repositorySearchService() {
    return repositorySearchService;
  }

  public static void shutdown() {
    verificationService.shutdown();
    databaseService.close();
  }

  public static VerificationService verificationService() {
    return verificationService;
  }

  public static ViewService viewService() {
    return viewService;
  }
}
