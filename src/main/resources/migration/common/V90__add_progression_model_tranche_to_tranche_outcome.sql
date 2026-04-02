ALTER TABLE tranche_outcome ADD COLUMN progression_model_tranche VARCHAR(32);
COMMENT ON COLUMN tranche_outcome.progression_model_tranche IS E'Description: Which tranche was selected for progression model or tranche 0 if no tranche was used. \nSource System: CRDS ';

