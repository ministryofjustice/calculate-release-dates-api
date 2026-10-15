-- Add indexes so delete of calculation request can check referential integrity faster
CREATE INDEX IF NOT EXISTS idx_crsui_calculation_request_user_input_id ON calculation_request_sentence_user_input(calculation_request_user_input_id);
CREATE INDEX IF NOT EXISTS idx_approved_dates_submission_cr_id ON approved_dates_submission(calculation_request_id);
CREATE INDEX IF NOT EXISTS idx_comparison_person_cr_id ON comparison_person(calculation_request_id);
CREATE INDEX IF NOT EXISTS idx_calculation_request_manual_reason_cr_id ON calculation_request_manual_reason(calculation_request_id);
