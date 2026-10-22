ALTER TABLE ai_chapter ADD COLUMN phase varchar(24) NOT NULL DEFAULT 'idle';
ALTER TABLE ai_chapter ADD COLUMN started_at timestamptz;
ALTER TABLE ai_chapter ADD COLUMN source_count integer NOT NULL DEFAULT 0;
ALTER TABLE ai_chapter ADD COLUMN output_chars integer NOT NULL DEFAULT 0;
ALTER TABLE ai_chapter ADD COLUMN summary text NOT NULL DEFAULT '';
