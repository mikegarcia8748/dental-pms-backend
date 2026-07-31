-- Firebase staff authentication: link app_user rows to Firebase identities while keeping the
-- self-hosted LOCAL break-glass account. Additive/relaxing only, so existing rows stay valid.

-- Firebase-authenticated users have no local password; only LOCAL (break-glass) users do.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

-- Immutable Firebase UID — the join key from a verified Firebase ID token to the local row.
-- Never join on email (a Firebase email can change). UNIQUE also backs the lookup index.
ALTER TABLE app_user ADD COLUMN firebase_uid VARCHAR(128) UNIQUE;

-- Which system owns this account's credentials. Existing rows backfill to LOCAL via the default.
ALTER TABLE app_user ADD COLUMN auth_source VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- Defense-in-depth behind the use-case guard: a LOCAL row must keep a password, a FIREBASE row
-- must carry a firebase_uid. Prevents a half-formed row that could authenticate ambiguously.
ALTER TABLE app_user ADD CONSTRAINT app_user_auth_source_ck CHECK (
    (auth_source = 'LOCAL' AND password_hash IS NOT NULL) OR
    (auth_source = 'FIREBASE' AND firebase_uid IS NOT NULL)
);
