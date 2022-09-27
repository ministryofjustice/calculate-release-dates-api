ALTER TABLE calculation_request ADD COLUMN offender_fine_payments JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN offender_fine_payments_version integer NULL;