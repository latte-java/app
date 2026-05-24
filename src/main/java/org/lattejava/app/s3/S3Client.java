/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.s3;

import module java.base;

/**
 * Access to an S3-compatible object store (Cloudflare R2, MinIO, AWS S3, ...). Implementations should not throw
 * checked exceptions; on transport failure they may throw a runtime exception that the caller is expected to surface.
 */
public interface S3Client {
  /**
   * Returns true if no objects exist under {@code prefix} in the configured bucket.
   *
   * @param prefix The key prefix to check (e.g. {@code "org/example/"}). Trailing slash is recommended.
   * @return True when the bucket has zero matching objects, false when at least one matches.
   */
  boolean isPrefixEmpty(String prefix);

  /**
   * Returns a short-lived presigned URL the caller can use to {@code PUT} an object at {@code key} into the configured
   * bucket.
   *
   * @param key    The object key (the full path within the bucket). Must not start with {@code /}.
   * @param expiry How long the URL is valid.
   * @return The presigned PUT URL.
   */
  String presignPut(String key, Duration expiry);
}
