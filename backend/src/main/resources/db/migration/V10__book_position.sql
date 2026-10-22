CREATE TABLE book_position (
  user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  book_id text NOT NULL,
  chapter_id text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, book_id)
);
