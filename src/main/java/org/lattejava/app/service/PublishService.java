/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service;

import module java.base;
import module org.lattejava.app;

import org.lattejava.app.s3.S3Client;
import org.lattejava.app.s3.S3HttpClient;
import org.lattejava.app.service.validation.PublishValidator;
import org.lattejava.app.service.validation.ValidationException;
import org.lattejava.web.Configuration;

public class PublishService {
  private static final Duration PRESIGN_EXPIRY = Duration.ofMinutes(15);
  private final PublishValidator validator;
  private final S3Client s3Client;

  public PublishService(Configuration config) {
    this(new PublishValidator(), new S3HttpClient(config));
  }

  /**
   * Test-only constructor. Production code should use the {@link #PublishService(Configuration)} constructor instead.
   */
  public PublishService(PublishValidator validator, S3Client s3Client) {
    this.s3Client = s3Client;
    this.validator = validator;
  }

  /**
   * Validates the requested key against {@code groupName} and returns a short-lived presigned PUT URL for it.
   *
   * @param groupName The target namespace (already path-bound and authorized).
   * @param fileName  The requested object key.
   * @return The presigned PUT URL.
   * @throws ValidationException If the body/key is invalid.
   * @throws org.lattejava.app.s3.S3Exception If the URL cannot be generated.
   */
  public String createPresignedURL(String groupName, String fileName) {
    Errors errors = validator.validate(groupName, fileName);
    if (!errors.empty()) {
      throw new ValidationException(errors);
    }
    return s3Client.presignPut(fileName, PRESIGN_EXPIRY);
  }
}
