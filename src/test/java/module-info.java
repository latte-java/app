/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.app.tests {
  requires java.net.http;
  requires java.sql;
  requires org.lattejava.app;
  requires org.lattejava.fusionauth;
  requires org.lattejava.http;
  requires org.lattejava.web;
  requires org.postgresql.jdbc;
  requires org.testng;

  opens org.lattejava.app.tests to org.testng;
  opens org.lattejava.app.tests.db to org.testng;
  opens org.lattejava.app.tests.github to org.testng;
  opens org.lattejava.app.tests.middleware to org.testng;
  opens org.lattejava.app.tests.s3 to org.testng;
  opens org.lattejava.app.tests.service to org.testng;
}
