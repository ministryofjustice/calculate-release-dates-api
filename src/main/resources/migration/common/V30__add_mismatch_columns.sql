alter table comparison_person add column is_match bool;
alter table comparison_person add column is_valid bool;
alter table comparison_person add column validation_messages JSONB;

alter table comparison add column number_of_people_compared bigint;