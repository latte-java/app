package org.lattejava.app.model;

public enum Role {
  OWNER("Owner", "Full control. Can delete the group."),
  ADMIN("Admin", "Manage members and settings."),
  PUBLISHER("Publisher", "Publish and yank artifact versions."),
  VIEWER("Viewer", "Read-only access to private artifacts.");

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
