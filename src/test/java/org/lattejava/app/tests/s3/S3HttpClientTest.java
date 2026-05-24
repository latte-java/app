/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.s3;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.Configuration;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Exercises {@link S3HttpClient} against the configured S3-compatible store. In the test environment this is the local
 * MinIO from the {@code cli}'s {@code minio} target (endpoint, bucket, and credentials come from
 * {@code src/test/resources/config.properties}). Requires MinIO running on {@code :9000} with the {@code latte-test}
 * bucket. The presigned-PUT round trip is covered end-to-end through the API by {@code PublishUploadTest}.
 */
@Test
public class S3HttpClientTest {
  public S3HttpClient client;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("s3.accessKeyId", "s3.bucket", "s3.endpoint", "s3.region", "s3.secretAccessKey"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new S3HttpClient(config);
  }

  @Test
  public void isPrefixEmpty_unknownPrefix_returnsTrue() {
    String unique = "test-empty-" + UUID.randomUUID() + "/";
    assertTrue(client.isPrefixEmpty(unique));
  }
}
