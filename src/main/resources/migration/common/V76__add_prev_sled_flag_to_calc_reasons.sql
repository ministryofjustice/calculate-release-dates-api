-- Add column to capture whether the calculation reason is one that is usable for previously recorded SLED
ALTER TABLE calculation_reason ADD COLUMN eligible_for_previously_recorded_sled boolean DEFAULT false NOT NULL;
COMMENT ON COLUMN calculation_reason.eligible_for_previously_recorded_sled IS E'Description: Whether to consider SLEDs from these calculations for previously recorded SLED \nSource System: CRDS ';

-- Make 2 day check the only one that is eligible for previously recorded SLED
UPDATE calculation_reason SET eligible_for_previously_recorded_sled = true WHERE display_name = '2 day check';
