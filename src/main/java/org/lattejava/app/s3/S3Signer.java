/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.s3;

import module java.base;

/**
 * AWS Signature V4 signer for an S3-compatible object store. Signs a single request as either a computed
 * {@code Authorization} header value or a query-string presigned URL. The region is supplied per call so the same
 * signer works against any provider (R2 uses {@code auto}, MinIO/AWS use a real region such as {@code us-east-1}).
 */
public final class S3Signer {
  public static final String ALGORITHM = "AWS4-HMAC-SHA256";
  public static final String EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  public static final String SERVICE = "s3";
  private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private S3Signer() {
  }

  public static String authorizationHeader(String method, String host, String path, SortedMap<String, String> query,
                                           String accessKeyId, String secretAccessKey, String region, Instant now) {
    String amzDate = AMZ_DATE.format(now);
    String shortDate = SHORT_DATE.format(now);
    String credentialScope = shortDate + "/" + region + "/" + SERVICE + "/aws4_request";

    String canonicalQuery = query.entrySet()
                                 .stream()
                                 .map(e -> uriEncode(e.getKey(), false) + "=" + uriEncode(e.getValue(), false))
                                 .collect(Collectors.joining("&"));
    String canonicalHeaders = "host:" + host + "\nx-amz-content-sha256:" + EMPTY_PAYLOAD_HASH + "\nx-amz-date:" + amzDate + "\n";
    String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

    String canonicalRequest = method + "\n" + path + "\n" + canonicalQuery + "\n"
        + canonicalHeaders + "\n" + signedHeaders + "\n" + EMPTY_PAYLOAD_HASH;

    String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

    byte[] signingKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), shortDate);
    signingKey = hmac(signingKey, region);
    signingKey = hmac(signingKey, SERVICE);
    signingKey = hmac(signingKey, "aws4_request");

    String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));

    return ALGORITHM
        + " Credential=" + accessKeyId + "/" + credentialScope
        + ", SignedHeaders=" + signedHeaders
        + ", Signature=" + signature;
  }

  public static String formatAmzDate(Instant now) {
    return AMZ_DATE.format(now);
  }

  /**
   * Builds an AWS SigV4 query-string presigned URL for a single request (no body signing — payload hash is
   * {@code UNSIGNED-PAYLOAD}, signed headers are {@code host} only). The returned URL can be handed to a client to
   * issue {@code method} directly against the object store.
   *
   * @param method          The HTTP method (e.g. {@code PUT}).
   * @param scheme          The URL scheme ({@code https} or {@code http}).
   * @param host            The request host (authority, may include a port).
   * @param path            The already-URI-encoded absolute path (e.g. {@code /bucket/org/lattejava/x.jar}).
   * @param accessKeyId     The access key id.
   * @param secretAccessKey The secret access key.
   * @param region          The signing region (e.g. {@code auto} for R2, {@code us-east-1} for MinIO/AWS).
   * @param expiry          How long the URL is valid.
   * @param now             The signing instant.
   * @return The presigned URL.
   */
  public static String presignedURL(String method, String scheme, String host, String path, String accessKeyId,
                                    String secretAccessKey, String region, Duration expiry, Instant now) {
    String amzDate = AMZ_DATE.format(now);
    String shortDate = SHORT_DATE.format(now);
    String credentialScope = shortDate + "/" + region + "/" + SERVICE + "/aws4_request";

    SortedMap<String, String> query = new TreeMap<>();
    query.put("X-Amz-Algorithm", ALGORITHM);
    query.put("X-Amz-Credential", accessKeyId + "/" + credentialScope);
    query.put("X-Amz-Date", amzDate);
    query.put("X-Amz-Expires", Long.toString(expiry.toSeconds()));
    query.put("X-Amz-SignedHeaders", "host");

    String canonicalQuery = query.entrySet()
                                 .stream()
                                 .map(e -> uriEncode(e.getKey(), false) + "=" + uriEncode(e.getValue(), false))
                                 .collect(Collectors.joining("&"));
    String canonicalHeaders = "host:" + host + "\n";
    String signedHeaders = "host";

    String canonicalRequest = method + "\n" + path + "\n" + canonicalQuery + "\n"
        + canonicalHeaders + "\n" + signedHeaders + "\nUNSIGNED-PAYLOAD";

    String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

    byte[] signingKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), shortDate);
    signingKey = hmac(signingKey, region);
    signingKey = hmac(signingKey, SERVICE);
    signingKey = hmac(signingKey, "aws4_request");

    String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));

    return scheme + "://" + host + path + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
  }

  /**
   * Per AWS docs, percent-encode every byte except unreserved characters (RFC 3986 section 2.3). For path components,
   * '/' is preserved.
   */
  public static String uriEncode(String input, boolean keepSlash) {
    StringBuilder sb = new StringBuilder();
    for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
          || c == '_' || c == '-' || c == '~' || c == '.' || (keepSlash && c == '/')) {
        sb.append(c);
      } else {
        sb.append('%').append(String.format("%02X", b & 0xFF));
      }
    }
    return sb.toString();
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  private static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
