ALTER TABLE store_buckets ADD COLUMN IF NOT EXISTS is_protected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE store_buckets 
SET is_protected = TRUE 
WHERE name = 'lola-relate-backups' 
   OR name LIKE '%-backups' 
   OR (name LIKE 'lola-%' AND name LIKE '%backup%');
