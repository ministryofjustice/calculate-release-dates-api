UPDATE calculation_request SET calculation_type = 'CALCULATED' WHERE calculation_type IS NULL;
ALTER TABLE calculation_request ALTER COLUMN calculation_type SET NOT NULL;
