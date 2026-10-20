-- Remove the original single-account constraint; keep all existing accounts and data.
ALTER TABLE app_user DROP COLUMN singleton;
