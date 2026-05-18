/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.util;

import module java.base;

/**
 * Builds Gravatar URLs from an email address.
 *
 * <p>The hash is the SHA-256 of the trimmed, lowercased email per the Gravatar 2024+ spec. An empty
 * or null email is treated as the empty string, which together with the {@code identicon} default
 * yields a stable but generic placeholder image.
 */
public final class Gravatar {
  public static final String BASE_URL = "https://gravatar.com/avatar/";

  private Gravatar() {
  }

  public static String hash(String email) {
    String normalized = email == null ? "" : email.trim().toLowerCase();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public static String url(String email, int size) {
    return BASE_URL + hash(email) + "?s=" + (size * 2) + "&d=identicon";
  }
}
