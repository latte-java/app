/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module java.net.http;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lattejava.app.s3.S3Signer;
import org.lattejava.web.oidc.Tokens;

import static org.testng.Assert.*;

/**
 * Full end-to-end publish: call {@code POST /api/v1/publish/{groupName}}, upload an artifact to the configured S3 store
 * (MinIO in the test environment) using the presigned URL the API returns, then fetch that object straight from MinIO
 * over HTTP and confirm it landed at the exact key with the exact bytes. This proves the minted URLs work against a
 * real S3 backend and write where the caller asked.
 * <p>
 * Requires MinIO running on {@code :9000} with the {@code latte-test} bucket set to anonymous download — start it with
 * {@code latte minio}. The running app under {@link BaseTest} is configured for MinIO via
 * {@code src/test/resources/config.properties}.
 */
@Test
public class PublishUploadTest extends BaseTest {
  private static final String CONTENT = "publish-upload-test-content";
  private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

  @AfterMethod
  public void clearRequestState() {
    test.clearRequestState();
  }

  @Test
  public void publish_thenUploadToS3_objectIsAtExpectedLocation() throws Exception {
    String key = "org/lattejava/upload-test-" + UUID.randomUUID() + "/artifact.txt";
    String objectURL = main.config.get("s3.endpoint") + "/" + main.config.get("s3.bucket") + "/" + S3Signer.uriEncode(key, true);

    try (HttpClient http = HttpClient.newHttpClient()) {
      // Precondition: nothing at the object's location yet (direct GET against the public test bucket).
      HttpResponse<Void> before = http.send(get(objectURL), HttpResponse.BodyHandlers.discarding());
      assertEquals(before.statusCode(), 404, "object should not exist yet at " + objectURL);

      // 1. Call the publish API to get a presigned PUT URL for the artifact key.
      Tokens tokens = oidcForAPI.login("test@lattejava.org", "password", "http://localhost:8888/callback");
      test.clearRequestState();
      test.clearCookies();
      WebTestAsserter asserter = test.withHeader("Authorization", "Bearer " + tokens.accessToken())
                                     .withHeader("Content-Type", "application/json")
                                     .withBody("{\"fileName\":\"" + key + "\"}")
                                     .post("/api/v1/publish/org.lattejava");
      asserter.assertStatus(200);

      String responseBody = new String(asserter.response().body(), StandardCharsets.UTF_8);
      Matcher matcher = URL_PATTERN.matcher(responseBody);
      assertTrue(matcher.find(), "response missing url: " + responseBody);
      String presignedURL = matcher.group(1);

      try {
        // 2. Upload the artifact bytes directly to S3 using only the presigned URL.
        HttpResponse<String> put = http.send(
            HttpRequest.newBuilder(URI.create(presignedURL)).PUT(HttpRequest.BodyPublishers.ofString(CONTENT)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(put.statusCode(), 200, "PUT to presigned URL failed: " + put.body());

        // 3. Fetch the object straight from MinIO at the expected URL and verify the exact bytes.
        HttpResponse<String> get = http.send(get(objectURL), HttpResponse.BodyHandlers.ofString());
        assertEquals(get.statusCode(), 200, "object not found at " + objectURL);
        assertEquals(get.body(), CONTENT, "object content mismatch at " + objectURL);
      } finally {
        deleteObject(key);
      }
    }
  }

  private static HttpRequest get(String url) {
    return HttpRequest.newBuilder(URI.create(url)).GET().build();
  }

  /**
   * Removes the uploaded object with a SigV4-signed DELETE so the test bucket stays clean. Uses {@link S3Signer}
   * directly rather than adding a delete method to the production {@link org.lattejava.app.s3.S3Client} (the app never
   * deletes objects).
   */
  private void deleteObject(String key) throws Exception {
    URI endpoint = URI.create(main.config.get("s3.endpoint"));
    String host = endpoint.getAuthority();
    String path = "/" + main.config.get("s3.bucket") + "/" + S3Signer.uriEncode(key, true);
    Instant now = Instant.now();
    String authorization = S3Signer.authorizationHeader("DELETE", host, path, new TreeMap<>(),
        main.config.get("s3.accessKeyId"), main.config.get("s3.secretAccessKey"), main.config.get("s3.region"), now);

    try (HttpClient http = HttpClient.newHttpClient()) {
      http.send(HttpRequest.newBuilder(URI.create(endpoint.getScheme() + "://" + host + path))
                           .header("Authorization", authorization)
                           .header("x-amz-content-sha256", S3Signer.EMPTY_PAYLOAD_HASH)
                           .header("x-amz-date", S3Signer.formatAmzDate(now))
                           .DELETE()
                           .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
