-- import of reference domain CALC_REASON from NOMIS

CREATE TABLE legacy_calculation_reason
(
    id SERIAL CONSTRAINT legacy_calculation_reason_pk PRIMARY KEY,
    code        VARCHAR(40),
    description VARCHAR(40),
    crds_reason_id INT REFERENCES calculation_reason (id)
);

COMMENT ON COLUMN legacy_calculation_reason.id IS E'Description: Primary key of the table. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_calculation_reason.code IS E'Description: The code in NOMIS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_calculation_reason.description IS E'Description: The description in NOMIS. \nSource System: NOMIS';
COMMENT ON COLUMN legacy_calculation_reason.crds_reason_id IS E'Description: The id of the reason to use in CRDS instead of this one. If there is no equivalent then we can use OTHER. \nSource System: NOMIS';


INSERT INTO legacy_calculation_reason (code, description, crds_reason_id)
VALUES
('ADJUST','Adjust Sentence', null),
('APPEAL','Appeal Decision',  (SELECT id FROM calculation_reason WHERE display_name = 'Appeal decision') ),
('CORRECTN','Correction of Earlier Sentence',  (SELECT id FROM calculation_reason WHERE display_name = 'Correcting an earlier sentence')),
('DELETE','Sentence Deleted', null),
('ERROR','Error in Original Calculation',  (SELECT id FROM calculation_reason WHERE display_name = 'Error in original calculation')),
('FINE','Pay Fine', null),
('FP','Fine Payment', null),
('FS','Further Sentence',  (SELECT id FROM calculation_reason WHERE display_name = 'Adding more sentences or terms')),
('FW','Further Warrant', null),
('HDC','Home Detention Curfew Approved',  (SELECT id FROM calculation_reason WHERE display_name = 'Recording a non-calculated date (including HDCAD, APD or ROTL)')),
('LR','Licence Revocation', null),
('LW','Lodged Warrant',  (SELECT id FROM calculation_reason WHERE display_name = 'Lodged warrant')),
('NEW','New Sentence',  (SELECT id FROM calculation_reason WHERE display_name = 'Initial calculation')),
('OFF_MOD','Offence Modified', null),
('OVERRIDE','Override', null),
('PFP','Part Payment of Fine', null),
('PTD','S244ZB Power to Detain', null),
('SV','Sentence Varied by Sentencing Court', null),
('TORER','Section 247A TORER 2020', null),
('TRANSFER','Transfer Check',  (SELECT id FROM calculation_reason WHERE display_name = 'Transfer check')),
('UPDATE','Modify Sentence', null);
