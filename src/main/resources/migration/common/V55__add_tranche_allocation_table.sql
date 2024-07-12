CREATE TABLE tranche_outcome
(
    id SERIAL CONSTRAINT tranche_outcome_pk PRIMARY KEY,
    calculation_request_id integer NOT NULL references calculation_request (id),
    outcome_date timestamp with time zone not null,
    tranche VARCHAR(16)
);
