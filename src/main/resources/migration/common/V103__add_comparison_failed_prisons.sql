-- Eligible prisons in a bulk "all" comparison that prison-api could not process.
ALTER TABLE comparison ADD COLUMN prisons_failed jsonb NOT NULL DEFAULT '[]';
