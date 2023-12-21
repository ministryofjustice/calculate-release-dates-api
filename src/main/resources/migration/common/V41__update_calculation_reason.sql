ALTER TABLE calculation_reason ADD COLUMN is_bulk BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE calculation_request
    ALTER COLUMN reason_for_calculation SET NOT NULL;

INSERT INTO calculation_reason (display_name, is_active, is_bulk)
VALUES ('Bulk Comparison', false, true);
