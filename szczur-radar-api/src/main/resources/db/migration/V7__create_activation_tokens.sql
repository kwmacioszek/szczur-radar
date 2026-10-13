-- One-time activation links (S-07): a link lets its holder set the account's password once. Only the
-- hex SHA-256 of the token is stored, so a database dump holds no usable link. At most one link per
-- account: a new link replaces the row (the previous link stops working), a used link deletes it.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.
CREATE TABLE activation_tokens (
    user_id    UUID        NOT NULL,
    -- hex SHA-256 of the token in the link
    token_hash TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT activation_tokens_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_activation_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_activation_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_activation_tokens_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
