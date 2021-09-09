CREATE TABLE calculation_request
(
    id                     serial      constraint calculation_request_pk PRIMARY KEY,
    calculation_reference  UUID        NOT NULL,
    prisoner_id            varchar(10) NOT NULL,
    booking_id             integer     NOT NULL,
    input_data             JSONB       NOT NULL,
    calculation_status     varchar(20) NOT NULL,
    calculated_at          timestamp with time zone,
    calculated_by_username varchar(20) NOT NULL
);
CREATE INDEX idx_calculation_request_reference ON calculation_request(calculation_reference);
CREATE INDEX idx_calculation_request_prisoner_id ON calculation_request(prisoner_id);
CREATE INDEX idx_calculation_request_booking_id ON calculation_request(booking_id);

CREATE TABLE calculation_outcome
(
    id                       serial      constraint calculation_outcome_pk PRIMARY KEY,
    calculation_reference    UUID        NOT NULL,
    calculation_date_type    varchar(20) NOT NULL,
    outcome_date             timestamp with time zone
);
