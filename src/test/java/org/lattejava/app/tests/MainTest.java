package org.lattejava.app.tests;

import module org.lattejava.web;
import module org.testng;

import org.lattejava.app.*;

@Test
public class MainTest {
  public Main main;
  public StringBodyAsserter string = new StringBodyAsserter();
  public WebTest test = new WebTest(Main.PORT);

  @AfterSuite
  public void afterSuite() {
    main.close();
  }

  @BeforeSuite
  public void beforeSuite() {
    main = new Main();
    main.main();
  }

  @Test
  public void getSlash() {
    test.get("/")
        .assertRedirect(301, "/app/dashboard");
  }
}
