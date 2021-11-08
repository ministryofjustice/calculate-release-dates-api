ALTER TABLE calculation_request ALTER COLUMN calculated_by_username TYPE VARCHAR(100);
ALTER TABLE calculation_request ALTER COLUMN calculation_status TYPE VARCHAR(100);
ALTER TABLE calculation_outcome ALTER COLUMN calculation_date_type TYPE VARCHAR(100);
