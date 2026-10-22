ALTER TABLE ai_chapter ADD COLUMN client_snapshot TEXT;
ALTER TABLE ai_chapter ADD COLUMN client_deadline TIMESTAMPTZ;
-- Magpie is now browser-only. Never retain a legacy gateway credential on the server.
UPDATE ai_provider SET ciphertext='' WHERE id='magpie';
