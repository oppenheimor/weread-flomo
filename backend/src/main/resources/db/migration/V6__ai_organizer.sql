CREATE TABLE ai_provider (
  user_id uuid NOT NULL REFERENCES app_user(id),
  id varchar(80) NOT NULL,
  name varchar(120) NOT NULL,
  protocol varchar(16) NOT NULL CHECK(protocol IN ('openai','anthropic','google')),
  base_url text NOT NULL,
  ciphertext text NOT NULL,
  models text NOT NULL DEFAULT '[]',
  PRIMARY KEY(user_id,id)
);
CREATE TABLE ai_default (
  user_id uuid PRIMARY KEY REFERENCES app_user(id),
  provider_id varchar(80) NOT NULL,
  model varchar(200) NOT NULL,
  FOREIGN KEY(user_id,provider_id) REFERENCES ai_provider(user_id,id) ON DELETE CASCADE
);
CREATE TABLE ai_chapter (
  user_id uuid NOT NULL REFERENCES app_user(id),
  book_id text NOT NULL,
  chapter_id text NOT NULL,
  job_id uuid,
  state varchar(16) NOT NULL DEFAULT 'idle' CHECK(state IN ('idle','running','failed','ready')),
  error text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,book_id,chapter_id)
);
CREATE TABLE ai_note (
  user_id uuid NOT NULL REFERENCES app_user(id),
  id text NOT NULL,
  book_id text NOT NULL,
  chapter_id text NOT NULL,
  position integer NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  snapshot text NOT NULL,
  edited boolean NOT NULL DEFAULT false,
  version integer NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,id),
  FOREIGN KEY(user_id,book_id,chapter_id) REFERENCES ai_chapter(user_id,book_id,chapter_id)
);
CREATE INDEX ai_note_chapter_idx ON ai_note(user_id,book_id,chapter_id);
