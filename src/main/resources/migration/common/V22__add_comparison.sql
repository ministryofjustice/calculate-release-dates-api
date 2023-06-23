-- Create new table to store a comparison
CREATE TABLE comparison
(
    id                          serial                        PRIMARY KEY,
    comparison_reference        UUID                          NOT NULL,
    comparison_short_reference  varchar(8)                    NOT NULL,
    criteria                    JSONB                         NOT NULL,
    calculated_at               timestamp with time zone      NOT NULL,
    calculated_by_username      varchar(20)                   NOT NULL,
    manual_input                boolean                       NOT NULL,
    prison                      varchar(5)
);

-- Create new table to store the identifier for people on which a comparison was run
CREATE TABLE comparison_person
(
    id                          serial                        PRIMARY KEY,
    comparison_id               int references comparison(id) NOT NULL,
    person                      varchar(20)                   NOT NULL
);


