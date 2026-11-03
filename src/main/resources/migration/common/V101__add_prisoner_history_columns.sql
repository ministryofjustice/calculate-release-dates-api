ALTER TABLE calculation_request ADD COLUMN prisoner_modified_reason VARCHAR(100) NULL;
ALTER TABLE calculation_request ADD COLUMN prisoner_modified_previous_prisoner_id VARCHAR(100) NULL;
ALTER TABLE calculation_request ADD COLUMN prisoner_modified_at TIMESTAMP WITH TIME ZONE NULL;
COMMENT ON COLUMN calculation_request.prisoner_modified_reason IS E'Description: If the request has been moved from another prisoner this lists the reason why, e.g., PRISONER_MERGE, BOOKING_MOVE, MIGRATION. \nSource System: CRDS ';
COMMENT ON COLUMN calculation_request.prisoner_modified_previous_prisoner_id IS E'Description: The previous prisoner id before the request was modified. \nSource System: CRDS ';
COMMENT ON COLUMN calculation_request.prisoner_modified_at IS E'Description: The date and time the prisoner id was modified. \nSource System: CRDS ';

ALTER TABLE approved_dates_submission ADD COLUMN prisoner_modified_reason VARCHAR(100) NULL;
ALTER TABLE approved_dates_submission ADD COLUMN prisoner_modified_previous_prisoner_id VARCHAR(100) NULL;
ALTER TABLE approved_dates_submission ADD COLUMN prisoner_modified_at TIMESTAMP WITH TIME ZONE NULL;
COMMENT ON COLUMN approved_dates_submission.prisoner_modified_reason IS E'Description: If the request has been moved from another prisoner this lists the reason why, e.g., PRISONER_MERGE, BOOKING_MOVE, MIGRATION. \nSource System: CRDS ';
COMMENT ON COLUMN approved_dates_submission.prisoner_modified_previous_prisoner_id IS E'Description: The previous prisoner id before the request was modified. \nSource System: CRDS ';
COMMENT ON COLUMN approved_dates_submission.prisoner_modified_at IS E'Description: The date and time the prisoner id was modified. \nSource System: CRDS ';
