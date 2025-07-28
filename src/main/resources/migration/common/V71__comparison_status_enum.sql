--Add enum field for status.
ALTER TABLE comparison ADD COLUMN comparison_status varchar(50);

--Set all historic comparisons to completed.
UPDATE comparison SET comparison_status = 'COMPLETED' WHERE id > 0;

--Make column now not null
ALTER TABLE comparison ALTER COLUMN comparison_status SET NOT NULL;

--Remove unused table
DROP TABLE comparison_status CASCADE;