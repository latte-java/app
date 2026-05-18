-- groups: keyed by reverse-DNS or short name. The name IS the identity.
CREATE TABLE groups (
  name              TEXT PRIMARY KEY,
  description       TEXT NOT NULL DEFAULT '',
  state             TEXT NOT NULL CHECK (state IN ('VERIFIED', 'PENDING', 'FAILED')),
  verification_code TEXT NULL,
  created_at        INTEGER NOT NULL,
  verified_at       INTEGER
);

-- members: group x user
CREATE TABLE members (
  group_name  TEXT    NOT NULL,
  user_id     TEXT    NOT NULL,
  role        TEXT    NOT NULL CHECK (role  IN ('OWNER', 'CONTRIBUTOR')),
  state       TEXT    NOT NULL CHECK (state IN ('PENDING', 'ACTIVE')),
  invited_by  TEXT,
  invited_at  INTEGER,
  joined_at   INTEGER,
  PRIMARY KEY (group_name, user_id),
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);

CREATE INDEX members_user_id_idx ON members(user_id);

-- group_verifications: one outstanding DNS TXT challenge per group
CREATE TABLE group_verifications (
  group_name      TEXT PRIMARY KEY,
  started_at      INTEGER NOT NULL,
  last_checked_at INTEGER,
  FOREIGN KEY (group_name) REFERENCES groups(name) ON DELETE CASCADE
);
