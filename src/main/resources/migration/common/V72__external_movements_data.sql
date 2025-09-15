ALTER TABLE calculation_request ADD COLUMN external_movements JSONB NULL;
ALTER TABLE calculation_request ADD COLUMN external_movements_version integer NULL;