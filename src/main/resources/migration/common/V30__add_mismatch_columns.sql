alter table comparison_person add column is_match bool not null;
alter table comparison_person add column is_valid bool not null;
alter table comparison_person add column validation_messages JSONB not null;

alter table comparison add column number_of_people_compared bigint;