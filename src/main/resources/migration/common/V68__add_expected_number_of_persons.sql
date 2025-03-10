alter table comparison add column number_of_people_expected bigint DEFAULT 1 NOT NULL;

update comparison set number_of_people_expected = number_of_people_compared;