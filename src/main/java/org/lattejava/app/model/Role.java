/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.model;

public enum Role {
  CONTRIBUTOR("Contributor", "Publish artifacts."),
  OWNER("Owner", "Manage members and publish artifacts.");

  private final String description;
  private final String label;

  Role(String label, String description) {
    this.label = label;
    this.description = description;
  }

  public String description() {
    return description;
  }

  public String label() {
    return label;
  }
}
