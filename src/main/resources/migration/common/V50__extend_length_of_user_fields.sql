ALTER TABLE comparison_person_discrepancy ALTER COLUMN created_by TYPE VARCHAR(240);
ALTER TABLE calculation_request ALTER COLUMN calculated_by_username TYPE VARCHAR(240);
ALTER TABLE comparison ALTER COLUMN calculated_by_username TYPE VARCHAR(240);
ALTER TABLE comparison_person ALTER COLUMN calculated_by_username TYPE VARCHAR(240);
ALTER TABLE approved_dates_submission ALTER COLUMN submitted_by_username TYPE VARCHAR(240);
