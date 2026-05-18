/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.r2;

import module java.base;
import module java.net.http;

import org.lattejava.web.Configuration;

public class R2HttpClient implements R2Client {
  private final String accessKeyId;
  private final String bucket;
  private final String host;
  private final HttpClient httpClient;
  private final String secretAccessKey;

  public R2HttpClient(Configuration config) {
    this.accessKeyId = config.get("r2.accessKeyId");
    this.bucket = config.get("r2.bucket");
    this.host = config.get("r2.accountId") + ".r2.cloudflarestorage.com";
    this.secretAccessKey = config.get("r2.secretAccessKey");
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
    String authorization = R2Signer.authorizationHeader("GET", host, path, query, accessKeyId, secretAccessKey, now);

    String queryString = query.entrySet()
                              .stream()
                              .map(e -> R2Signer.uriEncode(e.getKey(), false) + "=" + R2Signer.uriEncode(e.getValue(), false))
                              .collect(Collectors.joining("&"));

    HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + path + "?" + queryString))
                                     .timeout(Duration.ofSeconds(30))
                                     .header("Authorization", authorization)
                                     .header("x-amz-content-sha256", R2Signer.EMPTY_PAYLOAD_HASH)
                                     .header("x-amz-date", R2Signer.formatAmzDate(now))
                                     .GET()
                                     .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new R2Exception("R2 list request failed for prefix [" + prefix + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new R2Exception("R2 list request interrupted for prefix [" + prefix + "]", e);
    }
    if (response.statusCode() / 100 != 2) {
      throw new R2Exception("R2 list returned HTTP [" + response.statusCode() + "] for prefix [" + prefix + "]: [" + response.body() + "]");
    }
    // S3 ListObjectsV2: any object presence is signaled by <Contents> in the body.
    return !response.body().contains("<Contents>");
  }
}
