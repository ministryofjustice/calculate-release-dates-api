ALTER TABLE tranche_outcome
    ADD COLUMN affected_by_sds40 boolean DEFAULT false;

UPDATE tranche_outcome
SET affected_by_sds40 = true
WHERE tranche IN ('TRANCHE_1', 'TRANCHE_2')