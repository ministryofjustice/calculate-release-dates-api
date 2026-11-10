-- Bulk comparison of existing UNUSED_DEDUCTIONS adjustments against UnusedDeductionsCalculationService
CREATE TABLE unused_deductions_comparison
(
    id                                     serial                   PRIMARY KEY,
    comparison_reference                   UUID                     NOT NULL,
    comparison_short_reference             varchar(8)               NOT NULL,
    criteria                               JSONB                    NOT NULL,
    prison                                 varchar(5),
    comparison_type                        varchar(50)              NOT NULL,
    calculated_at                          timestamp with time zone NOT NULL,
    calculated_by_username                 varchar(40)              NOT NULL,
    comparison_status                      varchar(20)              NOT NULL,
    number_of_people_expected              bigint                   NOT NULL,
    number_of_people_compared              bigint                   NOT NULL,
    number_of_people_comparison_failed_for bigint                   NOT NULL
);

CREATE UNIQUE INDEX idx_unused_deductions_comparison_short_ref ON unused_deductions_comparison (comparison_short_reference);

CREATE TABLE unused_deductions_comparison_person
(
    id                             serial                                          PRIMARY KEY,
    comparison_id                  int references unused_deductions_comparison(id) NOT NULL,
    person                         varchar(20)                                     NOT NULL,
    last_name                      varchar(100),
    latest_booking_id              bigint,
    reference                      UUID                                            NOT NULL,
    short_reference                varchar(8)                                      NOT NULL,
    calculated_at                  timestamp with time zone                        NOT NULL,
    outcome                        varchar(30)                                     NOT NULL,
    existing_unused_deductions     bigint                                          NOT NULL,
    calculated_unused_deductions   bigint,
    custodial_days                 bigint,
    total_deductions               bigint,
    adjustments                    JSONB                                           NOT NULL,
    validation_messages            JSONB                                           NOT NULL,
    fatal_exception                varchar(256),
    establishment                  varchar(5)
);

CREATE INDEX idx_udcp_comparison_id ON unused_deductions_comparison_person (comparison_id);
