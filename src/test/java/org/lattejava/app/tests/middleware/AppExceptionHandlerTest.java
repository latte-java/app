/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.app.tests.middleware;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.web;
import module org.testng;

import static org.testng.Assert.*;

/**
 * Verifies the standalone error page renders correctly and that {@link AppExceptionHandler} actually catches downstream
 * exceptions and serves the page. The template is rendered with the same {@link JTETemplates} configuration
 * {@code Main} uses, so a syntax error or missing reference in {@code pages/error.jte} surfaces here rather than at
 * runtime when the server actually hits an unhandled exception.
 */
public class AppExceptionHandlerTest {
  private static final int PORT = 8082;

  @Test
  public void errorPage_500_rendersStatusDigitsAndThemedStackTrace() {
    JTETemplates templates = new JTETemplates(Path.of("web"), Path.of("build"));
    String html = templates.render("pages/error.jte", Map.of("status", 500));

    // Coffee-themed fake exception and stack-trace UI markers.
    assertTrue(html.contains("EspressoMachineUnpluggedException"), "error page should name the themed exception");
    assertTrue(html.contains("stacktrace.log"), "error page should render the fake stacktrace.log chrome");

    // The big status number renders as <first-digit><span sky-blue>middle</span><last-digit>.
    assertTrue(html.contains("5<span class=\"text-sky-500\">0</span>0"), "status digits should render as 5<span>0</span>0 for 500");

    // The Barista.pour line in the stack trace inlines the status code.
    assertTrue(html.contains("Barista.java:500"), "stack trace should include the status code in the Barista line");

    // Title carries the status.
    assertTrue(html.contains("<title>500 · Latte repository</title>"), "page title should include the status code");

    // Navigation back to the app.
    assertTrue(html.contains("href=\"/app/dashboard\""), "Back-to-dashboard link missing");
    assertTrue(html.contains("href=\"/app/groups/\""), "Browse-groups link missing");
  }

  @Test
  public void errorPage_otherStatus_rendersWithDifferentDigits() {
    JTETemplates templates = new JTETemplates(Path.of("web"), Path.of("build"));
    String html = templates.render("pages/error.jte", Map.of("status", 401));

    assertTrue(html.contains("<title>401 · Latte repository</title>"), "page title should include the status code");
    assertTrue(html.contains("4<span class=\"text-sky-500\">0</span>1"), "status digits should render as 4<span>0</span>1 for 401");
    assertTrue(html.contains("Barista.java:401"), "stack trace should inline the new status code");
  }

  @Test
  public void middleware_catchesDownstreamThrow_andServesErrorPage() {
    JTETemplates templates = new JTETemplates(Path.of("web"), Path.of("build"));
    WebTest test = new WebTest(PORT);
    var string = new StringBodyAsserter();
    try (Web _ = new Web().baseDir(Path.of("web"))
                          .install(new AppExceptionHandler(templates))
                          .get("/throw", (_, _) -> {
                              throw new IllegalStateException("kaboom");
                            })
                          .start(PORT)) {
      test.get("/throw")
          .assertStatus(500)
          .assertBodyAs(string, s -> s.contains("EspressoMachineUnpluggedException")
                                      .contains("Barista.java:500"));
    }
  }
}
