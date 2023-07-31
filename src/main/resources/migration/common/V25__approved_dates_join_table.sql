alter table approved_dates drop column approved_dates_submission_request_id;

create table APPROVED_DATES_SUBMISSION_APPROVED_DATES (
    approved_dates_id integer not null references approved_dates(id),
    approved_dates_submission_id integer not null references approved_dates_submission(id)
)