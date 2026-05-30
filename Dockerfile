# Copyright (c) 2026 The Latte Project
# SPDX-License-Identifier: MIT
#
# Runtime image for the Latte Java app. It ships the self-contained bundle produced by
# `latte bundle` (build/bundle: app.sh + lib/*.jar + web/ + build/jte-classes) onto a full
# JDK 25 -- a JDK, not a JRE, because JTE compiles templates at runtime with the in-process
# javac, and Main's entry point is a Java 25 instance main(). The base also provides bash,
# which app.sh requires.
FROM eclipse-temurin:25-jdk

WORKDIR /app
COPY build/bundle/ /app/

# The embedded web server listens on 8080 (org.lattejava.app.Main default).
EXPOSE 8080

# app.sh resolves its own directory, builds the explicit per-jar module path from lib/, and
# launches org.lattejava.app.Main. Config is supplied via environment variables.
CMD ["bash", "app.sh"]
