-- User roles (created only if missing, so this script can be re-run after dropping the table)
DO $$
BEGIN
    CREATE TYPE user_role AS ENUM ('HOST', 'CANDIDATE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

-- Users
CREATE TABLE users (
    user_id     BIGSERIAL    PRIMARY KEY,
    user_uuid   UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    user_name   VARCHAR(254) NOT NULL,                 -- user's email address, stored lowercase
    password    VARCHAR(100) NOT NULL,                 -- PBKDF2-HMAC-SHA512 hash (64 base64 chars), never plain text
    time_zone   VARCHAR(50)  NOT NULL,                 -- IANA zone id, e.g. 'Asia/Kolkata'
    user_role   user_role    NOT NULL,                 -- exactly one role per user, never changed
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_users_user_uuid UNIQUE (user_uuid)
);

-- Usernames are unique among active (non-deleted) users only,
-- so a soft-deleted user's username can be reused.
CREATE UNIQUE INDEX uk_users_user_name_active ON users (user_name) WHERE deleted = FALSE;

-- Refresh tokens. Only the SHA-256 hash is stored; each token is single-use (revoked when used).
CREATE TABLE refresh_tokens (
    refresh_token_id  BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (user_id),
    token_hash        VARCHAR(64)  NOT NULL,           -- hex SHA-256 of the token, never the token itself
    expires_at        TIMESTAMPTZ  NOT NULL,
    revoked           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Registration OTPs. One row per email (a new OTP replaces the old one); deleted once used, expired or
-- locked out. Only an HMAC of the OTP is stored, never the code.
CREATE TABLE email_otps (
    email_otp_id     BIGSERIAL    PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,            -- lowercase, like users.user_name
    otp_hash         VARCHAR(64)  NOT NULL,            -- hex HMAC-SHA256 of email + OTP
    expires_at       TIMESTAMPTZ  NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    last_sent_at     TIMESTAMPTZ  NOT NULL,            -- for the resend cooldown
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_email_otps_email UNIQUE (email)
);
