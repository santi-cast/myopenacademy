-- Learner accounts. The display name is kept as typed; uniqueness and login use the lower-case form.
CREATE TABLE code_academy.users (
    id                  uuid         PRIMARY KEY,
    username            varchar(32)  NOT NULL,
    username_canonical  varchar(32)  NOT NULL,
    password_hash       varchar(255) NOT NULL,
    credentials_version bigint       NOT NULL,
    created_at          timestamptz  NOT NULL,
    CONSTRAINT users_username_canonical_key UNIQUE (username_canonical),
    CONSTRAINT users_username_format CHECK (username ~ '^[A-Za-z0-9_]{3,32}$'),
    CONSTRAINT users_username_canonical_lower CHECK (username_canonical = lower(username)),
    CONSTRAINT users_credentials_version_positive CHECK (credentials_version > 0)
);
