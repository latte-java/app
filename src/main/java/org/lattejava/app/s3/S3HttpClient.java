/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.s3;

import module java.base;
import module java.net.http;

import org.lattejava.web.*;

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

  /**
   * Appends every {@code <Key>...</Key>} value in a ListObjectsV2 XML body to {@code keys}, XML-unescaping each.
   */
  private static void extractKeys(String body, List<String> keys) {
    int from = 0;
    while (true) {
      int start = body.indexOf("<Key>", from);
      if (start < 0) {
        break;
      }

      start += "<Key>".length();
      int end = body.indexOf("</Key>", start);
      if (end < 0) {
        break;
      }

      keys.add(xmlUnescape(body.substring(start, end)));
      from = end + "</Key>".length();
    }
  }

  /**
   * Returns the text of the first {@code <tag>...</tag>} in {@code body}, or null when absent.
   */
  private static String tagValue(String body, String tag) {
    String open = "<" + tag + ">";
    String close = "</" + tag + ">";
    int start = body.indexOf(open);
    if (start < 0) {
      return null;
    }

    start += open.length();
    int end = body.indexOf(close, start);
    return end < 0 ? null : body.substring(start, end);
  }

  private static String xmlUnescape(String s) {
    return s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&");
  }

  @Override
  public boolean isPrefixEmpty(String prefix) {
    SortedMap<String, String> query = new TreeMap<>();
    query.put("list-type", "2");
    query.put("max-keys", "1");
    query.put("prefix", prefix);

    var response = callS3(prefix, query);

    // S3 ListObjectsV2: any object presence is signaled by <Contents> in the body.
    return !response.body().contains("<Contents>");
  }

  @Override
  public List<String> listKeys(String prefix) {
    List<String> keys = new ArrayList<>();
    String continuationToken = null;

    // ListObjectsV2 returns at most 1000 keys per page; follow the continuation token until exhausted.
    do {
      SortedMap<String, String> query = new TreeMap<>();
      query.put("list-type", "2");
      query.put("prefix", prefix);
      if (continuationToken != null) {
        query.put("continuation-token", continuationToken);
      }

      var response = callS3(prefix, query);
      String body = response.body();
      extractKeys(body, keys);
      continuationToken = "true".equals(tagValue(body, "IsTruncated")) ? tagValue(body, "NextContinuationToken") : null;
    } while (continuationToken != null);

    return keys;
  }

  @Override
  public String presignPut(String key, Duration expiry) {
    String path = "/" + bucket + "/" + S3Signer.uriEncode(key, true);
    return S3Signer.presignedURL("PUT", scheme, host, path, accessKeyId, secretAccessKey, region, expiry, Instant.now());
  }

  private HttpResponse<String> callS3(String prefix, SortedMap<String, String> query) {
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

    return response;
  }
}
