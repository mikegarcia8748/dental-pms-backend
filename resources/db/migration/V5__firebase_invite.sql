-- Staff are now invited rather than created in Firebase: a SysAdmin writes the app_user row, and the
-- Firebase UID is bound on the staff member's first "Sign in with Google". The backend verifies ID
-- tokens against Google's public JWKS and holds no service-account credentials, so it cannot create
-- a Firebase user up front — which means a FIREBASE row must be allowed to exist with a NULL
-- firebase_uid while the invite is unclaimed.

ALTER TABLE app_user DROP CONSTRAINT app_user_auth_source_ck;

-- Same intent as V4 — a row must never be able to authenticate ambiguously — restated for the
-- invite model: LOCAL rows keep a password, FIREBASE rows never have one. firebase_uid is now
-- allowed to be NULL, meaning "invited, not yet signed in".
-- The UNIQUE index on firebase_uid still guarantees one row per Firebase identity: Postgres treats
-- NULLs as distinct, so any number of invites can be unclaimed at once.
ALTER TABLE app_user ADD CONSTRAINT app_user_auth_source_ck CHECK (
    (auth_source = 'LOCAL'    AND password_hash IS NOT NULL) OR
    (auth_source = 'FIREBASE' AND password_hash IS NULL)
);
