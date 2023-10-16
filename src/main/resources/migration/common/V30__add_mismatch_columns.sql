alter table comparison_person add column is_match bool;
alter table comparison_person add column is_valid bool;
alter table comparison_person add column validation_messages JSONB;