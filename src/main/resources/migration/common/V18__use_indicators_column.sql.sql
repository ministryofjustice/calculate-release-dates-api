-- Add column to capture whether we've used the offence indicators rather the user input.
ALTER TABLE calculation_request_user_input ADD COLUMN use_offence_indicators boolean DEFAULT false NOT NULL;