-- Calculation user input table contained details about sentences, table needs renaming.
ALTER TABLE calculation_request_user_input
  RENAME TO calculation_request_sentence_user_input;

-- Also rename primary key constraint
ALTER TABLE calculation_request_sentence_user_input
 RENAME CONSTRAINT calculation_request_user_input_pk TO calculation_request_sentence_user_input_pk;

-- Create new table to contain user inputs to a calculation, which are not related to a sentence.
CREATE TABLE calculation_request_user_input
(
  id                        serial      constraint calculation_request_user_input_pk PRIMARY KEY,
  calculation_request_id    integer     NOT NULL references calculation_request (id),
  calculate_ersed           boolean     DEFAULT false NOT NULL
);

-- Insert into the new table from existing sentence user inputs.
INSERT INTO calculation_request_user_input(calculation_request_id)
SELECT DISTINCT calculation_request_id FROM calculation_request_sentence_user_input;

-- Add column to link the sentence user inputs to new table.
ALTER TABLE calculation_request_sentence_user_input ADD COLUMN calculation_request_user_input_id integer NOT NULL references calculation_request_user_input (id);

-- Set the new column to match data from old table to new up.
UPDATE calculation_request_sentence_user_input sentence_user_input
SET sentence_user_input.calculation_request_user_input_id = user_input.id
FROM calculation_request_user_input user_input
WHERE sentence_user_input.calculation_request_id = user_input.calculation_request_id;

-- We no longer need the calculation_request_id on the old table. However it won't be dropped until migration is successful.
ALTER TABLE calculation_request_sentence_user_input ALTER COLUMN calculation_request_id DROP NOT NULL;