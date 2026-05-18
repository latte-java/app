/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

/**
 * Outcome of {@link VerificationService#verifyGitHub} for an {@code io.github.*} group.
 *
 * <ul>
 *   <li>{@code NOT_AUTHORIZED} — the user is not a member of the org or the login does not match the personal account.</li>
 *   <li>{@code NOT_LINKED} — the user has no GitHub IDP link in FusionAuth.</li>
 *   <li>{@code UNAUTHORIZED} — the GitHub token was rejected (401); the FA link was deleted.</li>
 *   <li>{@code VERIFIED} — verification succeeded; the group state was flipped to VERIFIED.</li>
 * </ul>
 */
public enum GitHubVerifyResult {
  NOT_AUTHORIZED,
  NOT_LINKED,
  UNAUTHORIZED,
  VERIFIED
}
