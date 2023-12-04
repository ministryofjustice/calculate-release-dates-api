delete from comparison_person;
delete from comparison;

alter table comparison_person add column mismatch_type varchar(50);