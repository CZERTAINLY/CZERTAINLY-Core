ALTER TABLE cmp_profile ADD COLUMN IF NOT EXISTS variant VARCHAR(255) NOT NULL DEFAULT 'VDEFAULT';