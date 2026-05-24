/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.s3;

import module java.base;
import module java.net.http;

import org.lattejava.web.Configuration;

/**
 * An {@link S3Client} backed by direct HTTP calls to an S3-compatible endpoint, using path-style addressing
 * ({@code <endpoint>/<bucket>/<key>}). The endpoint, region, bucket, and credentials all come from {@code s3.*}
 * configuration, so the same client targets Cloudflare R2, MinIO, or AWS S3.
 */
public class S3HttpClient implements S3Client {
  private final String accessKeyId;
  private final String bucket;
  private final String host;
  private final HttpClient httpClient;
  private final String region;
  private final String scheme;
  private final String secretAccessKey;

  public S3HttpClient(Configuration config) {
    URI endpoint = URI.create(config.get("s3.endpoint"));
    this.scheme = endpoint.getScheme();
    this.host = endpoint.getAuthority();
    this.region = config.get("s3.region");
    this.bucket = config.get("s3.bucket");
    this.accessKeyId = config.get("s3.accessKeyId");
    this.secretAccessKey = config.get("s3.secretAccessKey");
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public boolean isPrefixEmpty(String prefix) {
    SortedMap<String, String> query = new TreeMap<>();
    query.put("list-type", "2");
    query.put("max-keys", "1");
    query.put("prefix", prefix);

    Instant now = Instant.now();
    String path = "/" + bucket;
    String authorization = S3Signer.authorizationHeader("GET", host, path, query, accessKeyId, secretAccessKey, region, now);

    String queryString = query.entrySet()
                              .stream()
                              .map(e -> S3Signer.uriEncode(e.getKey(), false) + "=" + S3Signer.uriEncode(e.getValue(), false))
                              .collect(Collectors.joining("&"));

    HttpRequest request = HttpRequest.newBuilder(URI.create(scheme + "://" + host + path + "?" + queryString))
                                     .timeout(Duration.ofSeconds(30))
                                     .header("Authorization", authorization)
                                     .header("x-amz-content-sha256", S3Signer.EMPTY_PAYLOAD_HASH)
                                     .header("x-amz-date", S3Signer.formatAmzDate(now))
                                     .GET()
                                     .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new S3Exception("S3 list request failed for prefix [" + prefix + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new S3Exception("S3 list request interrupted for prefix [" + prefix + "]", e);
    }
    if (response.statusCode() / 100 != 2) {
      throw new S3Exception("S3 list returned HTTP [" + response.statusCode() + "] for prefix [" + prefix + "]: [" + response.body() + "]");
    }
    // S3 ListObjectsV2: any object presence is signaled by <Contents> in the body.
    return !response.body().contains("<Contents>");
  }

  @Override
  public String presignPut(String key, Duration expiry) {
    String path = "/" + bucket + "/" + S3Signer.uriEncode(key, true);
    return S3Signer.presignedURL("PUT", scheme, host, path, accessKeyId, secretAccessKey, region, expiry, Instant.now());
  }
}
