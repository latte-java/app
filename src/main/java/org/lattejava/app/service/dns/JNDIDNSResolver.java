/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.service.dns;

import module java.base;
import javax.naming.*;
import javax.naming.directory.*;

/**
 * Default {@link DNSResolver} backed by JNDI's DNS provider. Uses URL-scheme dispatch ({@code "dns:/" + host}) so the
 * implementation depends only on the public JNDI API and not on the {@code com.sun.jndi.dns} internal package.
 */
public class JNDIDNSResolver implements DNSResolver {
  @Override
  public List<String> lookupTXT(String host) {
    try {
      DirContext ctx = new InitialDirContext();
      try {
        Attributes attrs = ctx.getAttributes("dns:/" + host, new String[]{"TXT"});
        Attribute txt = attrs.get("TXT");
        if (txt == null) {
          return List.of();
        }
        List<String> result = new ArrayList<>(txt.size());
        NamingEnumeration<?> values = txt.getAll();
        while (values.hasMore()) {
          Object value = values.next();
          if (value != null) {
            result.add(value.toString());
          }
        }
        return result;
      } finally {
        ctx.close();
      }
    } catch (NamingException e) {
      return List.of();
    }
  }
}
