CREATE TABLE admin_telegram_recipient (
    id VARCHAR(30) PRIMARY KEY,
    credential_id BIGINT NOT NULL REFERENCES admin_telegram_credential(id) ON DELETE CASCADE,
    nickname VARCHAR(100) NOT NULL,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(credential_id, chat_id)
);
