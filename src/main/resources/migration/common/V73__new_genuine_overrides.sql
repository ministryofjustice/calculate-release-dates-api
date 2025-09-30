DROP TABLE genuine_override;


ALTER TABLE calculation_request ADD COLUMN overridden_by_calculation_request_id integer NULL;
ALTER TABLE calculation_request ADD COLUMN overrides_calculation_request_id integer NULL;
ALTER TABLE calculation_request ADD COLUMN genuine_override_reason VARCHAR(100) NULL;
ALTER TABLE calculation_request ADD COLUMN genuine_override_reason_further_detail VARCHAR(120);

ALTER TABLE calculation_request ADD CONSTRAINT FK_CALCULATIONREQUEST_OVERRIDDENBY_ON_CALCULATIONREQUESTID FOREIGN KEY (overridden_by_calculation_request_id) REFERENCES calculation_request (id);
ALTER TABLE calculation_request ADD CONSTRAINT FK_CALCULATIONREQUEST_OVERRIDES_ON_CALCULATIONREQUESTID FOREIGN KEY (overrides_calculation_request_id) REFERENCES calculation_request (id);

COMMENT ON COLUMN calculation_request.overridden_by_calculation_request_id IS E'A link to the new calculation this one was overridden by. \nSource System: CRDS ';
COMMENT ON COLUMN calculation_request.overrides_calculation_request_id IS E'A link to the original calculation this one was overrides. \nSource System: CRDS ';
COMMENT ON COLUMN calculation_request.genuine_override_reason IS E'A code representing the users selection for the reason the calculation was overridden. \nSource System: CRDS ';
COMMENT ON COLUMN calculation_request.genuine_override_reason_further_detail IS E'Free text for extra detail about the reason the calculation was overridden as entered by the user. \nSource System: CRDS ';
