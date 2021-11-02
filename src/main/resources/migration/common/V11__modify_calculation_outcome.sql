ALTER TABLE calculation_outcome DROP COLUMN calculation_reference;
ALTER TABLE calculation_outcome ADD COLUMN calculation_request_id integer NOT NULL references calculation_request (id);
ALTER TABLE calculation_outcome ALTER COLUMN outcome_date TYPE date;
ALTER TABLE calculation_outcome ALTER COLUMN outcome_date SET NOT NULL;
