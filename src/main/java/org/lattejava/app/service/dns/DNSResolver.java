/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.dns;

import module java.base;

/**
 * Looks up DNS TXT records for a host. Implementations must not throw checked exceptions; on resolution failure they
 * return an empty list (the caller treats failure as "not found yet").
 */
public interface DNSResolver {
  /**
   * Returns every TXT record value for {@code host}. Multi-string TXT records are concatenated by the underlying JNDI
   * provider into a single string; that's the form returned here.
   *
   * @param host The fully-qualified host to query (e.g. {@code "example.org"}).
   * @return All TXT values, or an empty list if the host has no TXT records or resolution failed.
   */
  List<String> lookupTXT(String host);
}
