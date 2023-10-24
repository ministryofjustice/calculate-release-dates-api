drop table comparison_person;

CREATE TABLE comparison_person
(
    id                          serial                        PRIMARY KEY,
    comparison_id               int references comparison(id) NOT NULL,
    person                      varchar(20)                   NOT NULL,
    latest_booking_id           bigint                        NOT NULL,
    is_match                    bool                          NOT NULL,
    is_valid                    bool                          NOT NULL,
    validation_messages         jsonb                         NOT NULL,
    reference                   UUID                          NOT NULL,
    short_reference             varchar(8)                    NOT NULL
);