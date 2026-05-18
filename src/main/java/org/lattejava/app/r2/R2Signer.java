/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.r2;

import module java.base;

/**
 * AWS Signature V4 signer for Cloudflare R2 (S3-compatible). Computes the {@code Authorization} header value for a
 * single GET request.
 */
public final class R2Signer {
  public static final String ALGORITHM = "AWS4-HMAC-SHA256";
  public static final String EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  public static final String REGION = "auto";
  public static final String SERVICE = "s3";
  private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private R2Signer() {
  }

  public static String authorizationHeader(String method, String host, String path, SortedMap<String, String> query,
                                           String accessKeyId, String secretAccessKey, Instant now) {
    String amzDate = AMZ_DATE.format(now);
    String shortDate = SHORT_DATE.format(now);
    String credentialScope = shortDate + "/" + REGION + "/" + SERVICE + "/aws4_request";

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
    signingKey = hmac(signingKey, REGION);
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
