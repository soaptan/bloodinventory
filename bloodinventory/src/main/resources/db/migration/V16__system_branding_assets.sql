CREATE TABLE IF NOT EXISTS system_branding_asset (
    asset_key VARCHAR(80) PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    asset_data BYTEA NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
