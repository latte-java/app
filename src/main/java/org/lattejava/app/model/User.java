package org.lattejava.app.model;

/**
 * The signed-in user, threaded into the main layout via the View shell.
 */
public record User(
    String email,           // primary identifier — there are no usernames
    String name
) {
}
