ALTER TABLE calculation_request ADD COLUMN return_to_custody_date JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN return_to_custody_date_version integer NULL;