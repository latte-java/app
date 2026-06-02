/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.app {
  requires com.fasterxml.jackson.databind;
  requires com.zaxxer.hikari;
  requires fusionauth.java.client;
  requires gg.jte;
  requires gg.jte.runtime;
  requires java.naming;
  requires java.net.http;
  requires org.jooq;
  requires org.lattejava.http;
  requires org.lattejava.jwt;
  requires org.lattejava.web;
  requires org.postgresql.jdbc;
  requires restify;

  exports org.lattejava.app;
  exports org.lattejava.app.db;
  exports org.lattejava.app.error;
  exports org.lattejava.app.github;
  exports org.lattejava.app.middleware;
  exports org.lattejava.app.model;
  exports org.lattejava.app.model.view;
  exports org.lattejava.app.s3;
  exports org.lattejava.app.security;
  exports org.lattejava.app.service;
  exports org.lattejava.app.service.dns;
  exports org.lattejava.app.service.validation;
  exports org.lattejava.app.util;

  // jOOQ reflectively instantiates the generated table-record classes.
  opens org.lattejava.app.db.jooq.tables.records to org.jooq;
}
