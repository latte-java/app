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
