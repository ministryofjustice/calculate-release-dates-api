ALTER TABLE calculation_reason ADD COLUMN nomis_reason VARCHAR(12) DEFAULT 'UPDATE';

UPDATE calculation_reason SET nomis_reason = 'UPDATE';

-- Nomis reasons mapped in ticket CRS-1779
UPDATE calculation_reason SET nomis_reason = 'NEW'      WHERE display_name = ('Initial calculation');
UPDATE calculation_reason SET nomis_reason = 'TRANSFER' WHERE display_name = ('Transfer check');
UPDATE calculation_reason SET nomis_reason = 'LW'       WHERE display_name = ('Lodged warrant');
UPDATE calculation_reason SET nomis_reason = 'ADJUST'   WHERE display_name = ('ADA - Additional Days Awarded');
UPDATE calculation_reason SET nomis_reason = 'ADJUST'   WHERE display_name = ('RADA - Restoration of Added Days Awarded');
UPDATE calculation_reason SET nomis_reason = 'FS'       WHERE display_name = ('Further sentences and terms');
UPDATE calculation_reason SET nomis_reason = 'APPEAL'   WHERE display_name = ('Appeal decision');

-- Design tweaks from review
UPDATE calculation_reason SET display_name = 'ADA (Additional days awarded)' WHERE display_name = ('ADA - Additional Days Awarded');
UPDATE calculation_reason SET display_name = 'RADA (Restoration of added days awarded)'  WHERE display_name = ('RADA - Restoration of Added Days Awarded');
UPDATE calculation_reason SET display_name = 'Further sentences or terms'   WHERE display_name = ('Further sentences and terms');