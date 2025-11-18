-- Add column to capture whether the user chose to use a previously entered SLED if available or not
ALTER TABLE calculation_request_user_input ADD COLUMN use_previously_recorded_sled_if_found boolean DEFAULT false NOT NULL;
COMMENT ON COLUMN calculation_request_user_input.use_previously_recorded_sled_if_found IS E'Description: Whether to use a previously recorded SLED if found or not \nSource System: CRDS ';

-- Change to store just the SLED override rather than a generic historic override.
ALTER TABLE calculation_outcome_historic_override RENAME TO calculation_outcome_historic_sled_override;
ALTER TABLE calculation_outcome_historic_sled_override DROP COLUMN historic_calculation_outcome_id;
ALTER TABLE calculation_outcome_historic_sled_override DROP COLUMN calculation_outcome_id;
ALTER TABLE calculation_outcome_historic_sled_override ADD COLUMN historic_calculation_request_id BIGINT references calculation_request (id);

-- Remove existing overrides from dev & preprod environments and then ensure there is only one override per calculation request
TRUNCATE calculation_outcome_historic_sled_override;
ALTER TABLE calculation_outcome_historic_sled_override
    ADD CONSTRAINT uc_calculation_outcome_historic_sled_override_calc_request_id UNIQUE (calculation_request_id);
