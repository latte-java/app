module org.lattejava.app.tests {
  requires org.lattejava.app;
  requires org.lattejava.http;
  requires org.lattejava.web;
  requires org.testng;

  opens org.lattejava.app.tests to org.testng;
}
