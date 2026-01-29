ALTER TABLE calculation_reason
    ADD COLUMN further_detail_description VARCHAR(256);
COMMENT ON COLUMN calculation_reason.further_detail_description IS E'Description: A description of what the further detail is for, this helps the UI provide hints and errors. \nSource System: CRDS ';

UPDATE calculation_reason
SET further_detail_description = 'the reason for the calculation'
WHERE display_name = 'Other';

UPDATE calculation_reason
SET further_detail_description = 'the name of the legislative change'
WHERE display_name = 'Legislative Changes';
