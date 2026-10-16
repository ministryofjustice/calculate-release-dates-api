-- Add more indexes so delete of calculation request can check referential integrity faster
CREATE INDEX IF NOT EXISTS idx_cr_overrides_calculation_request_id ON calculation_request(overrides_calculation_request_id);
CREATE INDEX IF NOT EXISTS idx_cr_overridden_by_calculation_request_id ON calculation_request(overridden_by_calculation_request_id);

-- drop duplicate index
DROP INDEX IF EXISTS idx_approved_dates_submission_cr_id;
