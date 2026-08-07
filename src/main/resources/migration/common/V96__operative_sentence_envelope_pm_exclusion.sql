ALTER TABLE operative_sentence_envelope
    ADD COLUMN contains_offence_excluded_from_progression_model BOOLEAN;
COMMENT ON COLUMN operative_sentence_envelope.contains_offence_excluded_from_progression_model IS E'Description: Whether the sentence envelope contained an excluded offence \nSource System: CRDS ';
