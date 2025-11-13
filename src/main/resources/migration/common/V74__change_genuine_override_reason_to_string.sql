UPDATE calculation_request SET genuine_override_reason = 'TRIAL_RECORD_OR_BREAKDOWN_DOES_NOT_MATCH_OVERALL_SENTENCE_LENGTH' WHERE genuine_override_reason = '0';
UPDATE calculation_request SET genuine_override_reason = 'AGGRAVATING_FACTOR_OFFENCE' WHERE genuine_override_reason = '1';
UPDATE calculation_request SET genuine_override_reason = 'POWER_TO_DETAIN' WHERE genuine_override_reason = '2';
UPDATE calculation_request SET genuine_override_reason = 'CROSS_BORDER_SECTION_RELEASE_DATE' WHERE genuine_override_reason = '3';
UPDATE calculation_request SET genuine_override_reason = 'RELEASE_DATE_FROM_ANOTHER_CUSTODY_PERIOD' WHERE genuine_override_reason = '4';
UPDATE calculation_request SET genuine_override_reason = 'OTHER', genuine_override_reason_further_detail = 'ERS (Early release scheme) breach' WHERE genuine_override_reason = '5';
UPDATE calculation_request SET genuine_override_reason = 'OTHER', genuine_override_reason_further_detail = 'Court of appeal' WHERE genuine_override_reason = '6';
UPDATE calculation_request SET genuine_override_reason = 'OTHER' WHERE genuine_override_reason = '7';