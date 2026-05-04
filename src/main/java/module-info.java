module org.lattejava.app {
  requires gg.jte;
  requires gg.jte.runtime;
  requires org.lattejava.http;
  requires org.lattejava.jwt;
  requires org.lattejava.web;
  exports org.lattejava.app;
  exports org.lattejava.app.model;
  exports org.lattejava.app.service;
  exports org.lattejava.app.util;
}
