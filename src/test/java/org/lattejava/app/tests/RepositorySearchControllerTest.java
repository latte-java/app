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

import static org.testng.Assert.*;

/**
 * End-to-end coverage for {@code GET /api/v1/repository/search} (the Java port of the repository-search Worker). Seeds
 * artifact objects into the configured S3 store (MinIO in tests) under a unique prefix, then exercises the public,
 * unauthenticated endpoint: full version list (newest first), {@code latest=true}, the 404 for an unknown artifact, and
 * the 400s for a missing/invalid {@code id}.
 * <p>
 * Requires MinIO running on {@code :9000} ({@code latte minio}); the app under {@link BaseTest} is configured for it
 * via {@code src/test/resources/config.properties}.
 */
@Test
public class RepositorySearchControllerTest extends BaseTest {
  private S3HttpClient s3;

  // Built here rather than in a field initializer: TestNG instantiates test classes during collection, before
  // BaseTest's @BeforeSuite sets the static `main`, so a field initializer touching main.config would NPE.
  @BeforeClass
  public void beforeClass() {
    s3 = new S3HttpClient(main.config);
  }

  @AfterMethod
  public void clear() {
    test.clearRequestState();
  }

  @Test
  public void search_invalidId_returns400() {
    var string = new StringBodyAsserter();
    test.get("/api/v1/repository/search?id=notvalid")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"fieldErrors\"").contains("[invalid]id"));
  }

  @Test
  public void search_latest_returnsOnlyNewest() throws Exception {
    String project = "searchtest-" + UUID.randomUUID();
    String prefix = "org/lattejava/" + project + "/";
    List<String> keys = List.of(prefix + "1.0.0/a-1.0.0.jar", prefix + "1.2.0/a-1.2.0.jar");
    putObjects(keys);
    try {
      var string = new StringBodyAsserter();
      test.get("/api/v1/repository/search?id=org.lattejava:" + project + "&latest=true")
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("\"versions\":[\"1.2.0\"]"));
    } finally {
      deleteObjects(keys);
    }
  }

  @Test
  public void search_missingId_returns400() {
    var string = new StringBodyAsserter();
    test.get("/api/v1/repository/search")
        .assertStatus(400)
        .assertBodyAs(string, s -> s.contains("\"fieldErrors\"").contains("[missing]id"));
  }

  @Test
  public void search_returnsVersionsNewestFirst() throws Exception {
    String project = "searchtest-" + UUID.randomUUID();
    String prefix = "org/lattejava/" + project + "/";
    List<String> keys = List.of(prefix + "0.9.0/a-0.9.0.jar", prefix + "1.0.0/a-1.0.0.jar", prefix + "1.2.0/a-1.2.0.jar");
    putObjects(keys);
    try {
      var string = new StringBodyAsserter();
      test.get("/api/v1/repository/search?id=org.lattejava:" + project)
          .assertStatus(200)
          .assertBodyAs(string, s -> s.contains("\"versions\":[\"1.2.0\",\"1.0.0\",\"0.9.0\"]"));
    } finally {
      deleteObjects(keys);
    }
  }

  @Test
  public void search_unknownArtifact_returns404() {
    test.get("/api/v1/repository/search?id=org.lattejava:nope-" + UUID.randomUUID())
        .assertStatus(404);
  }

  private void deleteObjects(List<String> keys) throws Exception {
    URI endpoint = URI.create(main.config.get("s3.endpoint"));
    String host = endpoint.getAuthority();
    try (HttpClient http = HttpClient.newHttpClient()) {
      for (String key : keys) {
        String path = "/" + main.config.get("s3.bucket") + "/" + S3Signer.uriEncode(key, true);
        Instant now = Instant.now();
        String authorization = S3Signer.authorizationHeader("DELETE", host, path, new TreeMap<>(),
            main.config.get("s3.accessKeyId"), main.config.get("s3.secretAccessKey"), main.config.get("s3.region"), now);
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

  private void putObjects(List<String> keys) throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      for (String key : keys) {
        HttpResponse<String> put = http.send(
            HttpRequest.newBuilder(URI.create(s3.presignPut(key, Duration.ofMinutes(5))))
                       .PUT(HttpRequest.BodyPublishers.ofString("x"))
                       .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(put.statusCode(), 200, "seed PUT failed for " + key + ": " + put.body());
      }
    }
  }
}
