CREATE TABLE calculation_request_user_input
(
    id                     serial      constraint calculation_request_user_input_pk PRIMARY KEY,
    calculation_request_id integer NOT NULL references calculation_request (id),
    offence_code varchar(20),
    nomis_matches boolean,
    sentence_sequence integer,
    type varchar(50),
    user_choice boolean
);
