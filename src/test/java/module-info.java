/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.app.tests {
  requires fusionauth.java.client;
  requires org.lattejava.app;
  requires org.lattejava.web;
  requires org.testng;
  requires restify;

  opens org.lattejava.app.tests to org.testng;
  opens org.lattejava.app.tests.db to org.testng;
  opens org.lattejava.app.tests.github to org.testng;
  opens org.lattejava.app.tests.r2 to org.testng;
  opens org.lattejava.app.tests.service to org.testng;
}
