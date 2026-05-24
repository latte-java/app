/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.s3;

import module java.base;
import module org.lattejava.app;

import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class S3SignerTest {
  @Test
  public void presignedURL_structureAndDeterminism() {
    Instant fixed = Instant.parse("2026-05-23T12:00:00Z");
    String url1 = S3Signer.presignedURL("PUT", "https", "acct.r2.cloudflarestorage.com",
        "/bucket/org/lattejava/x.jar", "AKID", "SECRET", "auto", Duration.ofMinutes(15), fixed);
    String url2 = S3Signer.presignedURL("PUT", "https", "acct.r2.cloudflarestorage.com",
        "/bucket/org/lattejava/x.jar", "AKID", "SECRET", "auto", Duration.ofMinutes(15), fixed);

    assertEquals(url1, url2);
    assertTrue(url1.startsWith("https://acct.r2.cloudflarestorage.com/bucket/org/lattejava/x.jar?"));
    assertTrue(url1.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
    assertTrue(url1.contains("X-Amz-Credential=AKID%2F20260523%2Fauto%2Fs3%2Faws4_request"));
    assertTrue(url1.contains("X-Amz-Date=20260523T120000Z"));
    assertTrue(url1.contains("X-Amz-Expires=900"));
    assertTrue(url1.contains("X-Amz-SignedHeaders=host"));
    assertTrue(url1.matches(".*X-Amz-Signature=[0-9a-f]{64}$"));
    assertTrue(url1.endsWith("&X-Amz-Signature=003b85b9eeb557127a831ebf621bbc2d8c931c3e05da1181c9cfbe9cb9c878de"),
        "Unexpected signature in: " + url1);
  }
}
