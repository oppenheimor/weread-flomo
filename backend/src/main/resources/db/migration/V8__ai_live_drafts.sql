-- Display-only partial cards. Never part of the deliverable ai_note table.
ALTER TABLE ai_chapter ADD COLUMN drafts text NOT NULL DEFAULT '[]';
