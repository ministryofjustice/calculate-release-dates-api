ALTER TABLE calculation_reason
    ADD COLUMN is_second_check BOOLEAN DEFAULT false NOT NULL;

INSERT INTO calculation_reason(display_rank, display_name,  nomis_comment, nomis_reason, is_bulk, is_active, is_second_check)
VALUES (150, 'To record a second check (also known as a management check)','To record a second check','UPDATE', false, true, true)
