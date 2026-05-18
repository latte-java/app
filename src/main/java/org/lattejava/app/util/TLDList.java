/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.util;

import module java.base;
import module java.net.http;

/**
 * Cached IANA top-level-domain list. Loaded once at startup; only a JVM restart reloads.
 */
public class TLDList {
  public static final String IANA_URL = "https://data.iana.org/TLD/tlds-alpha-by-domain.txt";
  private final Set<String> tlds;

  public TLDList(Set<String> tlds) {
    this.tlds = Set.copyOf(tlds);
  }

  public static TLDList fromIana() {
    try {
      try (var client = HttpClient.newHttpClient()) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(IANA_URL))
                                         .timeout(Duration.ofSeconds(30))
                                         .GET()
                                         .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
          throw new IllegalStateException("IANA TLD fetch returned HTTP [" + response.statusCode() + "]");
        }
        return fromText(response.body());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to download IANA TLD list from [" + IANA_URL + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while downloading IANA TLD list", e);
    }
  }

  public static TLDList fromText(String content) {
    Set<String> tlds = content.lines()
                              .map(String::trim)
                              .filter(line -> !line.isBlank() && !line.startsWith("#"))
                              .map(line -> line.toLowerCase(Locale.ROOT))
                              .collect(Collectors.toSet());
    return new TLDList(tlds);
  }

  public boolean contains(String tld) {
    return tld != null && tlds.contains(tld.toLowerCase(Locale.ROOT));
  }
}
