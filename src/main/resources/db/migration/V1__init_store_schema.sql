CREATE TABLE IF NOT EXISTS store_buckets (
    id UUID PRIMARY KEY,
    name VARCHAR(63) NOT NULL UNIQUE,
    region VARCHAR(50) NOT NULL DEFAULT 'lola-local',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    access_policy VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
    versioning_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_buckets_name ON store_buckets(name);

CREATE TABLE IF NOT EXISTS store_access_keys (
    id UUID PRIMARY KEY,
    access_key VARCHAR(128) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    bucket_id UUID REFERENCES store_buckets(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_access_keys_key ON store_access_keys(access_key);
