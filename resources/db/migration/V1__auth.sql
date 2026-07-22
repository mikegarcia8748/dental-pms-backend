-- Authentication schema: accounts and revocable refresh tokens.

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE refresh_token (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
