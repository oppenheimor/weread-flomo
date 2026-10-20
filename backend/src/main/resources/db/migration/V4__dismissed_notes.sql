CREATE TABLE dismissed_source (
  user_id uuid NOT NULL REFERENCES app_user(id),
  source_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, source_key)
);
