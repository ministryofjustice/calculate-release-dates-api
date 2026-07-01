ALTER TABLE calculation_reason
    ADD COLUMN is_second_check BOOLEAN DEFAULT false NOT NULL;

INSERT INTO calculation_reason(display_rank, display_name, nomis_comment, nomis_reason, is_bulk, is_active, is_second_check)
VALUES (150, 'To record a second check (also known as a management check)', 'Second Check', 'UPDATE', false, true, true);

CREATE TABLE calculation_request_second_check
(
    id                     SERIAL      CONSTRAINT calculation_request_second_check_pk PRIMARY KEY,
    calculation_request_id INTEGER     NOT NULL CONSTRAINT fk_calculation_request_second_check REFERENCES calculation_request (id),
    prisoner_id            VARCHAR(10) NOT NULL,
    checked_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_by_username    VARCHAR(240) NOT NULL
);

CREATE INDEX idx_calculation_request_second_check_calculation_request_id ON calculation_request_second_check(calculation_request_id);
CREATE INDEX idx_calculation_request_second_check_prisoner_id ON calculation_request_second_check(prisoner_id);