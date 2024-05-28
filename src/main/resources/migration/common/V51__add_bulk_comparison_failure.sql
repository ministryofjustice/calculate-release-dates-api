alter table comparison add column number_of_people_comparison_failed_for bigint DEFAULT 0 NOT NULL;
alter table comparison_person add column fatal_exception VARCHAR(256);