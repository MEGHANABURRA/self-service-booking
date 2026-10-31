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
