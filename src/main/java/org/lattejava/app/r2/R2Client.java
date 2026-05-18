/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.r2;

/**
 * Cloudflare R2 access. Implementations should not throw checked exceptions; on transport
 * failure they may throw a runtime exception that the caller is expected to surface.
 */
public interface R2Client {
  /**
   * Returns true if no objects exist under {@code prefix} in the configured bucket.
   *
   * @param prefix The key prefix to check (e.g. {@code "org/example/"}). Trailing slash is recommended.
   * @return True when the bucket has zero matching objects, false when at least one matches.
   */
  boolean isPrefixEmpty(String prefix);
}
