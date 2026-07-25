-- Initial PostgreSQL schema for the Latte Java app.
--
-- Migrations in this directory are applied on the classpath by org.lattejava.database's Migrator when the app starts
-- (see DatabaseService). Files are named <semver>.sql and applied in SemVer order; each applied file is recorded in
-- the `versions` table with its SHA-256 checksum, so an applied migration must never be edited — add a new migration
-- instead. Timestamps are epoch-millis stored as BIGINT (mapped to java.time.Instant via a jOOQ forced-type
-- converter); enums are TEXT + CHECK (mapped to the Java enums via jOOQ forced-type EnumConverters).

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

-- Reserved group for the project itself. Verified state, no verification code.
-- The owner membership row is inserted by the test fixture at runtime, since the FusionAuth test
-- user UUID is generated at kickstart time.
INSERT INTO groups (name, description, state, verification_code, created_at, verified_at)
VALUES (
           'org.lattejava',
           'Reserved group for the Latte Java project.',
           'VERIFIED',
           NULL,
           1714867200000,  -- 2024-05-05T00:00:00Z (arbitrary but stable)
           1714867200000
       )
    ON CONFLICT (name) DO NOTHING;

