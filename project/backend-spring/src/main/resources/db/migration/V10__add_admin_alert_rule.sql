CREATE TABLE admin_alert_rule (
    id VARCHAR(30) PRIMARY KEY,
    credential_id BIGINT NOT NULL REFERENCES admin_telegram_credential(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(credential_id, event_type)
);
