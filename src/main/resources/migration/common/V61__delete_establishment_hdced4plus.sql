
-- remove all the comparison_people for the ESTABLISHMENT_HDCED4PLUS runs
delete from comparison_person where comparison_person.comparison_id in (select comparison.id from comparison where comparison.comparison_type = 'ESTABLISHMENT_HDCED4PLUS');

-- remove all the comparisons for ESTABLISHMENT_HDCED4PLUS runs
delete from comparison where comparison_type = 'ESTABLISHMENT_HDCED4PLUS';