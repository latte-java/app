#!/usr/bin/env bash
#
# Copyright (c) 2026 The Latte Project
# SPDX-License-Identifier: MIT
#
# Runs the Latte Java app web server from a built bundle, mirroring `latte run`.
#
# Expected layout (produced by the `bundle` target in project.latte):
#   build/bundle/app.sh   - this script
#   build/bundle/lib/     - the app jar and all runtime dependencies
#   build/bundle/web/     - the JTE templates and static assets
#
# Honors JAVA_HOME (falls back to `java` on PATH) and forwards JAVA_OPTS to the JVM.
set -euo pipefail

# Resolve the bundle directory (where this script lives) and run from there, so the
# app's relative paths (`web`, the JTE `build` work dir, and config lookups) resolve.
BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$BUNDLE_DIR"

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

exec "$JAVA_BIN" \
  ${JAVA_OPTS:-} \
  --module-path lib \
  --module org.lattejava.app/org.lattejava.app.Main \
  "$@"
