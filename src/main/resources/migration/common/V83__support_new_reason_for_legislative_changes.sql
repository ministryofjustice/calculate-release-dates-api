-- new column to indicate whether the reason requires free text further details as with 'OTHER'.
ALTER TABLE calculation_reason
    ADD COLUMN requires_further_detail boolean DEFAULT false NOT NULL;
COMMENT ON COLUMN calculation_reason.requires_further_detail IS E'Description: Whether to require the user to enter additional details about this reason \nSource System: CRDS ';

INSERT INTO calculation_reason (display_name,
                                nomis_reason,
                                nomis_comment,
                                display_rank,
                                is_active,
                                is_other,
                                is_bulk,
                                eligible_for_previously_recorded_sled,
                                requires_further_detail)
VALUES ('Legislative Changes',
        'UPDATE',
        'Legislative Changes',
        135, -- between error in calculation and other
        true,
        false,
        false,
        false,
        true);

UPDATE calculation_reason
SET requires_further_detail = true
WHERE display_name = 'Other';
