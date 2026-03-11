ALTER TABLE calculation_request_sentence ADD COLUMN group_index integer NOT NULL default -1;
COMMENT ON COLUMN calculation_request_sentence.group_index IS E'Description: An identifier for grouped sentences that were treated as one during calculation, consecutive sentences for example. \nSource System: CRDS ';
