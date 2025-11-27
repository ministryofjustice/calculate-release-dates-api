-- Some validation codes were renamed but are used in the manual calculation reason table
UPDATE calculation_request_manual_reason SET code = 'INCORRECT_OFFENCE_ENCOURAGING_OR_ASSISTING' WHERE code = 'UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING';
UPDATE calculation_request_manual_reason SET code = 'INCORRECT_OFFENCE_GENERIC_CONSPIRACY' WHERE code = 'UNSUPPORTED_GENERIC_CONSPIRACY_OFFENCE';
UPDATE calculation_request_manual_reason SET code = 'INCORRECT_OFFENCE_BREACH_97' WHERE code = 'UNSUPPORTED_BREACH_97';
UPDATE calculation_request_manual_reason SET code = 'INCORRECT_SUSPENDED_OFFENCE' WHERE code = 'UNSUPPORTED_SUSPENDED_OFFENCE';
