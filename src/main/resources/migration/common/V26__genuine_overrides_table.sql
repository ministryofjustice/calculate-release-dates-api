create table genuine_override
(
    id          serial constraint genuine_override_pk PRIMARY KEY,
    reason                       text,
    original_calculation_request_id integer references calculation_request (id),
    saved_calculation_id            integer references calculation_request (id),
    is_overridden                boolean                  not null,
    saved_at                     timestamp with time zone not null
);
