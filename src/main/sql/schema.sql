-- PostgreSQL schema for the Latte Java app.
--
-- This file is the single source of truth for the schema: the `database` build target loads it into
-- the dev/test databases, and jOOQ code generation introspects the resulting database. Timestamps are
-- epoch-millis stored as BIGINT (mapped to java.time.Instant via a jOOQ forced-type converter); enums
-- are TEXT + CHECK (mapped to the Java enums via jOOQ forced-type EnumConverters). No migration tool is
-- used yet — when one is added, this becomes the first migration unchanged.

-- groups: keyed by reverse-DNS or short name. The name IS the identity.
CREATE TABLE groups (
  name              TEXT PRIMARY KEY,
  description       TEXT   NOT NULL DEFAULT '',
  state             TEXT   NOT NULL CHECK (state IN ('VERIFIED', 'PENDING', 'FAILED')),
  verification_code TEXT   NULL,
  created_at        BIGINT NOT NULL,
  verified_at       BIGINT
);

-- members: group x user
CREATE TABLE members (
  group_name  TEXT   NOT NULL,
  user_id     UUID   NOT NULL,
  role        TEXT   NOT NULL CHECK (role  IN ('OWNER', 'CONTRIBUTOR')),
  state       TEXT   NOT NULL CHECK (state IN ('PENDING', 'ACTIVE')),
  invited_by  UUID,
  invited_at  BIGINT,
  joined_at   BIGINT,
  PRIMARY KEY (group_name, user_id),
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);

CREATE INDEX members_user_id_idx ON members(user_id);

-- group_verifications: one outstanding DNS TXT challenge per group
CREATE TABLE group_verifications (
  group_name      TEXT PRIMARY KEY,
  started_at      BIGINT NOT NULL,
  last_checked_at BIGINT,
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);
