ALTER TABLE calculation_request_user_input
  RENAME TO calculation_request_sentence_user_input;

ALTER TABLE calculation_request_sentence_user_input
 RENAME CONSTRAINT calculation_request_user_input_pk TO calculation_request_sentence_user_input_pk;

CREATE TABLE calculation_request_user_input
(
  id                        serial      constraint calculation_request_user_input_pk PRIMARY KEY,
  calculation_request_id    integer     NOT NULL references calculation_request (id),
  calculate_ersed           boolean     DEFAULT false NOT NULL
);


INSERT INTO calculation_request_user_input(calculation_request_id)
SELECT calculation_request_id FROM calculation_request_sentence_user_input;

ALTER TABLE calculation_request_sentence_user_input ADD COLUMN calculation_request_user_input_id integer NOT NULL references calculation_request_user_input (id);


UPDATE calculation_request_sentence_user_input sentence_user_input
SET sentence_user_input.calculation_request_user_input_id = user_input.id
FROM calculation_request_user_input user_input
WHERE sentence_user_input.calculation_request_id = sentence_user_input.calculation_request_id;


ALTER TABLE calculation_request_sentence_user_input
DROP COLUMN calculation_request_id;