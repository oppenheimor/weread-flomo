-- One independent link per shelf/book. Closing removes the capability permanently.
CREATE TABLE public_share (
    user_id uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    book_id varchar(100) NOT NULL DEFAULT '',
    token varchar(43) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, book_id)
);
