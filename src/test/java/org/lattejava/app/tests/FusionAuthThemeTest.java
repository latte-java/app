/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests;

import module java.base;
import module java.net.http;
import module org.testng;

/**
 * Verifies the kickstart-provisioned tenant-level FusionAuth Simple Theme is applied to the hosted identity pages, and
 * that the theme's assets are actually loadable from the FusionAuth login page. The hosted login HTML must reference
 * the Latte Java logo served by the app's static server (proving the custom theme is in effect rather than FusionAuth's
 * stock default theme), and the app must serve {@code /static} assets with a Cross-Origin-Resource-Policy that permits
 * the cross-origin FusionAuth login page to embed them — without weakening that policy anywhere else.
 *
 * @author Brian Pontarelli
 */
@Test
public class FusionAuthThemeTest extends BaseTest {
  @Test
  public void hostedLoginPageUsesLatteTheme() throws Exception {
    String redirectURI = "http://localhost:8081" + main.ssrSettings.callbackPath();
    String query = "client_id=" + URLEncoder.encode(main.ssrConfig.clientId(), StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectURI, StandardCharsets.UTF_8)
        + "&response_type=code"
        + "&scope=" + URLEncoder.encode(String.join(" ", main.ssrConfig.scopes()), StandardCharsets.UTF_8)
        + "&state=themetest";
    URI authorize = URI.create(main.ssrConfig.authorizeEndpoint() + "?" + query);

    try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder(authorize).GET().build(),
          HttpResponse.BodyHandlers.ofString());

      Assert.assertEquals(response.statusCode(), 200,
          "Expected the hosted login page, got [" + response.statusCode() + "]: [" + response.body() + "]");
      Assert.assertTrue(response.body().contains("/static/images/logo.svg"),
          "Hosted login page is not using the Latte Simple Theme (no Latte logo URL in the HTML). Body: ["
              + response.body() + "]");
    }
  }

  @Test
  public void staticAssetsAllowCrossOriginEmbedding() throws Exception {
    try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      HttpResponse<Void> logo = client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:8081/static/images/logo.svg")).GET().build(),
          HttpResponse.BodyHandlers.discarding()
      );
      Assert.assertEquals(logo.statusCode(), 200, "Expected the static logo to be served, got [" + logo.statusCode() + "]");
      Assert.assertEquals(logo.headers().firstValue("Cross-Origin-Resource-Policy").orElse(null), "cross-origin",
          "Theme assets under /static must be cross-origin embeddable so the FusionAuth hosted login page (a "
              + "different origin) can load the logo and favicons.");

      HttpResponse<Void> appPage = client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:8081/")).GET().build(),
          HttpResponse.BodyHandlers.discarding());
      Assert.assertEquals(appPage.headers().firstValue("Cross-Origin-Resource-Policy").orElse(null), "same-origin",
          "Non-static responses must keep the strict same-origin CORP; the relaxation is scoped to /static only.");
    }
  }
}
