package org.lattejava.app.tests;

import module org.lattejava.web;
import module org.testng;

import org.lattejava.app.*;

@Test
public class MainTest {
  public Main main;
  public OIDCTestFixture oidc;
  public WebTest test = new WebTest(Main.PORT);

  @AfterMethod
  public void afterMethod() {
    oidc.logout();
  }

  @AfterSuite
  public void afterSuite() {
    main.close();
  }

  @BeforeSuite
  public void beforeSuite() {
    main = new Main();
    main.main();

    oidc = new OIDCTestFixture(test, main.oidcConfig);
  }

  @Test
  public void getSlash() {
    test.get("/")
        .assertRedirect(301, "/app/dashboard");
  }

  @Test
  public void oidcRedirect() {
    test.get("/app/dashboard")
        .assertRedirect(302, "/login")
        .reset(ResetItem.Request)
        .get("/login")
        .assertStatus(302)
        .assertHeaderStartsWith("Location", "http://localhost:9011/oauth2/authorize");
  }

  @Test
  public void alreadyLoggedInButRedirectsToOIDC() throws Exception {
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/login")
        .assertHeaderStartsWith("Location", "http://localhost:9011/oauth2/authorize");
  }

  @Test
  public void dashboard() throws Exception {
    var string = new StringBodyAsserter();
    oidc.login("test@lattejava.org", "password", "e9fdb985-9173-4e01-9d73-ac2d60d1dc8e");
    test.get("/app/dashboard")
        .assertStatus(200)
        .assertBodyAs(string, s -> s.contains("<body"));
  }
}
