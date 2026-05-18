/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.r2;

import module java.base;
import module org.lattejava.app;

import org.lattejava.web.Configuration;
import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class R2HttpClientTest {
  public R2HttpClient client;

  @BeforeClass
  public void beforeClass() {
    Configuration config = new Configuration(
        List.of("r2.accessKeyId", "r2.accountId", "r2.bucket", "r2.secretAccessKey"),
        Path.of(System.getProperty("user.home"), ".config", "latte", "app", "config.properties"),
        Path.of("src/test/resources/config.properties")
    );
    client = new R2HttpClient(config);
  }

  @Test
  public void isPrefixEmpty_unknownPrefix_returnsTrue() {
    String unique = "test-empty-" + UUID.randomUUID() + "/";
    assertTrue(client.isPrefixEmpty(unique));
  }
}
