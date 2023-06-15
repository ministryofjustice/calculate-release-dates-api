ALTER TABLE calculation_outcome ALTER COLUMN outcome_date DROP NOT NULL;
ALTER TABLE calculation_request ADD COLUMN calculation_type varchar(255);