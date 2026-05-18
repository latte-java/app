/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.db;

import module java.base;

/**
 * Static helpers for extracting typed values from D1 row maps. D1 returns each row as a
 * {@code Map<String, Object>}; these helpers handle the common cast/null/parse patterns
 * with a single call.
 */
public final class D1Tools {
  private D1Tools() {
  }

  public static Instant optionalInstant(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? null : Instant.ofEpochMilli(((Number) value).longValue());
  }

  public static String optionalString(Map<String, Object> row, String column) {
    return (String) row.get(column);
  }

  public static UUID optionalUUID(Map<String, Object> row, String column) {
    String value = (String) row.get(column);
    return value == null ? null : UUID.fromString(value);
  }

  public static <E extends Enum<E>> E requiredEnum(Map<String, Object> row, String column, Class<E> type) {
    String value = (String) row.get(column);
    if (value == null) {
      throw new IllegalStateException("Required column [" + column + "] was NULL");
    }
    return Enum.valueOf(type, value);
  }

  public static Instant requiredInstant(Map<String, Object> row, String column) {
    Object value = row.get(column);
    if (value == null) {
      throw new IllegalStateException("Required column [" + column + "] was NULL");
    }
    return Instant.ofEpochMilli(((Number) value).longValue());
  }

  public static String requiredString(Map<String, Object> row, String column) {
    String value = (String) row.get(column);
    if (value == null) {
      throw new IllegalStateException("Required column [" + column + "] was NULL");
    }
    return value;
  }

  public static UUID requiredUUID(Map<String, Object> row, String column) {
    String value = (String) row.get(column);
    if (value == null) {
      throw new IllegalStateException("Required column [" + column + "] was NULL");
    }
    return UUID.fromString(value);
  }
}
