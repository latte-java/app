package org.lattejava.app.model;

public record Member(
    String email,          // primary identifier
    String name,           // null if invitation not yet accepted — show email as fallback
    Role role,
    String avatar,
    String joined,         // free-text join date — empty for invited members
    boolean invited        // true while invitation is outstanding
) {
}
