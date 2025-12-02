-- Add column to capture whether the calculation reason is one that should be used for adding approved dates
ALTER TABLE calculation_reason ADD COLUMN use_for_approved_dates boolean DEFAULT false NOT NULL;
COMMENT ON COLUMN calculation_reason.use_for_approved_dates IS E'Description: Whether this is the reason to use for adding approved dates \nSource System: CRDS';

-- Set the flag on approved dates reason and ensure only one true
UPDATE calculation_reason SET use_for_approved_dates = true WHERE display_name = 'Recording a non-calculated date (including HDCAD, APD or ROTL)';
CREATE UNIQUE INDEX idx_calculation_reason_use_for_approved_dates_only_one_true ON calculation_reason (use_for_approved_dates) WHERE (use_for_approved_dates);
