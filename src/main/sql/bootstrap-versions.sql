-- One-time bootstrap of the migration `versions` table for a database that already has the schema and seed data
-- applied by hand (production). New databases never need this — the app's Migrator (see DatabaseService) creates the
-- table and applies the classpath migrations in src/main/resources/db itself on startup.
--
-- This marks the initial migrations as already applied so the Migrator skips them instead of failing on the existing
-- tables. The checksums are the lowercase hex SHA-256 of the raw bytes of each migration file, exactly as the Migrator
-- records them (verify with `shasum -a 256 src/main/resources/db/*.sql`). If those files change before this script is
-- run, the checksums below must be regenerated to match.
--
-- Run once with psql against the production database:
--
--   psql "<prod connection url>" -f src/main/sql/bootstrap-versions.sql

CREATE TABLE versions (
    version VARCHAR(255) NOT NULL PRIMARY KEY,
    checksum CHAR(64) NOT NULL,
    installed_instant BIGINT NOT NULL
);

INSERT INTO versions (version, checksum, installed_instant) VALUES
    ('0.1.0', '5134f9f31cfa627710df145f0246d9607238621e7e692bc99c86c23b5bce1833', (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT);
