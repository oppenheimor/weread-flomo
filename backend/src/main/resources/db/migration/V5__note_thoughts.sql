CREATE TABLE note_thought (
  user_id uuid NOT NULL REFERENCES app_user(id),
  note_id text NOT NULL,
  thought text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, note_id)
);
