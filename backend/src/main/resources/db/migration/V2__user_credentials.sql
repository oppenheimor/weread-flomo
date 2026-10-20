CREATE TABLE user_credential (
  user_id uuid NOT NULL REFERENCES app_user(id),
  provider varchar(16) NOT NULL CHECK(provider IN ('weread','flomo')),
  ciphertext text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,provider)
);
