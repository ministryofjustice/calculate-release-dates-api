ALTER TABLE calculation_reason ADD COLUMN is_bulk BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO calculation_reason (display_name, is_active, is_bulk)
VALUES ('Bulk Comparison', false, true);
