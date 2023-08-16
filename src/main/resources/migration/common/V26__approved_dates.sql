create table approved_dates_submission
(
    id                     serial
        constraint approved_dates_request_pk primary key,
    calculation_request_id integer                  NOT NULL references calculation_request (id),
    prisoner_id            varchar(10)              not null,
    booking_id             integer                  not null,
    submitted_at           timestamp with time zone not null,
    submitted_by_username  varchar(20)              not null
);

CREATE INDEX idx_approved_dates_submission_prisoner_id ON approved_dates_submission (prisoner_id);
CREATE INDEX idx_approved_dates_submission_booking_id ON approved_dates_submission (booking_id);

create table approved_dates
(
    id                                   serial
        constraint approved_dates_outcome_pk primary key,
    approved_dates_submission_request_id integer     not null references approved_dates_submission (id),
    calculation_date_type                varchar(20) not null,
    outcome_date                         timestamp with time zone
);
