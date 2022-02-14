ALTER TABLE calculation_request ADD COLUMN sentence_and_offences JSONB NOT NULL DEFAULT '[]'::JSONB;
ALTER TABLE calculation_request ADD COLUMN prisoner_details JSONB NOT NULL DEFAULT '{}'::JSONB;
ALTER TABLE calculation_request ADD COLUMN adjustments JSONB NOT NULL DEFAULT '{}'::JSONB;