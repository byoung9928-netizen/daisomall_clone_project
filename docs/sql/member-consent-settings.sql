USE daisomall;
CREATE TABLE member_consent_settings (
    member_id VARCHAR(100) NOT NULL,
    marketing_sms TINYINT(1) NOT NULL DEFAULT 0,
    marketing_email TINYINT(1) NOT NULL DEFAULT 0,
    marketing_personal_info TINYINT(1) NOT NULL DEFAULT 0,
    location_terms TINYINT(1) NOT NULL DEFAULT 0,
    retention_period ENUM(
        'WITHDRAWAL',
        '5_YEARS',
        '3_YEARS'
    ) NOT NULL DEFAULT 'WITHDRAWAL',
    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (member_id),

    CONSTRAINT fk_member_consent_settings_member
        FOREIGN KEY (member_id)
        REFERENCES members(member_id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;