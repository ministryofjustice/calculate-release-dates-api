ALTER TABLE calculation_reason ALTER COLUMN display_name TYPE VARCHAR(80);
ALTER TABLE calculation_reason ADD COLUMN nomis_comment VARCHAR(40);
ALTER TABLE calculation_reason ADD COLUMN display_rank INT NOT NULL DEFAULT 0;



UPDATE calculation_reason SET display_rank = 10, nomis_comment = 'Initial calculation' where display_name = 'Initial calculation';
UPDATE calculation_reason SET display_rank = 20, nomis_comment = 'Transfer check' where display_name = 'Transfer check';
UPDATE calculation_reason SET display_rank = 30, nomis_comment = 'Lodged warrant' where display_name = 'Lodged warrant';
UPDATE calculation_reason SET display_rank = 40, nomis_comment = 'ADA (Additional days awarded)' where display_name = 'ADA (Additional days awarded)';
UPDATE calculation_reason SET display_rank = 50, nomis_comment = 'RADA (Restoration of added days awarded)' where display_name = 'RADA (Restoration of added days awarded)';
UPDATE calculation_reason SET display_rank = 60, nomis_comment = 'Recording a non-calculated date' where display_name = 'Recording a non-calculated date';
UPDATE calculation_reason SET display_rank = 70, display_name = 'Recording a non-calculated date (including HDC, APD or ROTL)' where display_name = 'Recording a non-calculated date';
UPDATE calculation_reason SET display_rank = 80, nomis_comment = 'Adding more sentences or terms', display_name='Adding more sentences or terms' where display_name = 'Further sentences or terms';
UPDATE calculation_reason SET display_rank = 90, nomis_comment = '14 day check' where display_name = '14 day check';
UPDATE calculation_reason SET display_rank = 100, nomis_comment = '2 day check' where display_name = '2 day check';
UPDATE calculation_reason SET display_rank = 110, nomis_comment = 'Appeal decision' where display_name = 'Appeal decision';
UPDATE calculation_reason SET display_rank = 140, nomis_comment = 'Other' where display_name = 'Other';
UPDATE calculation_reason SET nomis_comment = 'Reason for calculation not provided' where display_name = 'Reason for calculation not provided';
UPDATE calculation_reason SET nomis_comment = 'Bulk Comparison' where display_name = 'Bulk Comparison';


ALTER TABLE calculation_reason ALTER COLUMN nomis_comment SET NOT NULL;
ALTER TABLE calculation_reason ALTER COLUMN display_rank SET NOT NULL;

INSERT INTO calculation_reason(display_rank, display_name,  nomis_comment, nomis_reason, is_bulk, is_active)
VALUES (120, 'Correcting an earlier sentence','Correcting an earlier sentence','CORRECTN', false, true),
    (130, 'Error in original calculation','Error in original calculation','ERROR ',false, true);
