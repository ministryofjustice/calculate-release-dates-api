
update comparison_person set mismatch_type = 'VALIDATION_ERROR' where comparison_person.mismatch_type = 'VALIDATION_ERROR_HDC4_PLUS';
update comparison_person set mismatch_type = 'UNSUPPORTED_SENTENCE_TYPE' where comparison_person.mismatch_type = 'UNSUPPORTED_SENTENCE_TYPE_FOR_HDC4_PLUS';
