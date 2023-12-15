ALTER TABLE comparison_person ADD COLUMN IF NOT EXISTS sds_plus_sentences_identified jsonb NOT NULL default '{}'::jsonb;

ALTER TABLE comparison ADD COLUMN comparison_type varchar(50);

UPDATE comparison SET comparison_type = 'ESTABLISHMENT_FULL' WHERE manual_input = false;
UPDATE comparison SET comparison_type = 'MANUAL' WHERE manual_input = true;

ALTER TABLE comparison ALTER COLUMN comparison_type SET NOT NULL;

CREATE INDEX idx_comparison_type ON comparison (comparison_type);

ALTER TABLE comparison DROP COLUMN manual_input;
