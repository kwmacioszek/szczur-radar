-- Accounts that are not people: login_enabled = false means the account can never log in, so it never
-- holds a password (ck_users_login_password). Existing accounts keep logging in (the default).
-- The scheduler account requests the daily pipeline run (S-05, POST /internal/pipeline-runs/daily), so
-- pipeline_runs.requested_by names a real account behind its foreign key. Its id and e-mail are the
-- constant user/SystemAccount.SCHEDULER in the code.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

ALTER TABLE users ADD COLUMN login_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD CONSTRAINT ck_users_login_password CHECK (login_enabled OR password_hash IS NULL);

INSERT INTO users (id, email, role, password_hash, login_enabled)
VALUES ('00000000-0000-4000-8000-000000000001', 'scheduler@system.invalid', 'user', NULL, false);
