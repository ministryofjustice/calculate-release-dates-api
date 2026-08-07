ALTER TABLE calculation_request_sentence ADD COLUMN awarded_during_custody NUMERIC;
COMMENT ON COLUMN calculation_request_sentence.awarded_during_custody IS E'Description: The amount of ADAs applied to the release date \nSource System: CRDS ';

ALTER TABLE calculation_request_sentence ADD COLUMN awarded_after_determinate_release NUMERIC;
COMMENT ON COLUMN calculation_request_sentence.awarded_after_determinate_release IS E'Description: The amount of ADAs applied post release \nSource System: CRDS ';
