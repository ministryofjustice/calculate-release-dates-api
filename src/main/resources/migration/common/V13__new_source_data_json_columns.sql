ALTER TABLE calculation_request ADD COLUMN sentence_and_offences JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN sentence_and_offences_version integer NULL;
ALTER TABLE calculation_request ADD COLUMN prisoner_details JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN prisoner_details_version integer NULL;
ALTER TABLE calculation_request ADD COLUMN adjustments JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN adjustments_version integer NULL;
ALTER TABLE calculation_request ADD COLUMN breakdown_html text NULL;
