ALTER TABLE comparison_person ADD COLUMN breakdown_by_release_date_type jsonb NOT NULL default '{}'::jsonb;