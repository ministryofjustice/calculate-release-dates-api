-- Enforce uniqueness of the CRDS calculation reference.
ALTER TABLE calculation_request ADD CONSTRAINT calculation_request_reference_uk UNIQUE (calculation_reference);

-- Replaced by the unique constraint's index.
DROP INDEX IF EXISTS idx_calculation_request_reference;

CREATE TABLE legacy_sentence_calculations (
    -- Source-assigned NOMIS calculation identity.
    -- No generated sequence.
                                                     nomis_calculation_id               bigint NOT NULL,

    -- Person identifier supplied as enrichment by sync.
                                                     prisoner_id                        varchar(10) NOT NULL,

                                                     booking_id                         bigint NOT NULL,

    -- Optional CRDS reference extracted from the source comment.
                                                     crds_calculation_reference         uuid,
    -- Optional resolved relationship to a CRDS calculation request.
                                                     calculation_request_id            integer,

    -- Calculation metadata.  
                                                     calculation_date                  timestamp(0) without time zone NOT NULL,
                                                     comment_text                      varchar(240),
                                                     calculation_reason_code           varchar(32) NOT NULL,

    -- Home detention curfew dates.
                                                     hdced_calculated_date              date,
                                                     hdced_overridden_date              date,
                                                     hdcad_calculated_date              date,
                                                     hdcad_overridden_date              date,

    -- Early, mid and late term dates.
                                                     etd_calculated_date                date,
                                                     etd_overridden_date                date,
                                                     mtd_calculated_date                date,
                                                     mtd_overridden_date                date,
                                                     ltd_calculated_date                date,
                                                     ltd_overridden_date                date,

    -- Release and parole dates.
                                                     ard_calculated_date                date,
                                                     ard_overridden_date                date,
                                                     crd_calculated_date                date,
                                                     crd_overridden_date                date,
                                                     ped_calculated_date                date,
                                                     ped_overridden_date                date,
                                                     apd_calculated_date                date,
                                                     apd_overridden_date                date,
                                                     npd_calculated_date                date,
                                                     npd_overridden_date                date,

    -- Licence and sentence expiry dates.
                                                     led_calculated_date                date,
                                                     led_overridden_date                date,
                                                     sed_calculated_date                date,
                                                     sed_overridden_date                date,

    -- Post-recall and tariff dates.
                                                     prrd_calculated_date               date,
                                                     prrd_overridden_date               date,
                                                     tariff_calculated_date             date,
                                                     tariff_overridden_date             date,
                                                     dprrd_calculated_date              date,
                                                     dprrd_overridden_date              date,

    -- Date types stored only as overrides in the source.
                                                     ersed_overridden_date              date,
                                                     tersed_overridden_date             date,
                                                     rotl_overridden_date               date,

    -- Top-up supervision expiry dates.
                                                     tused_calculated_date              date,
                                                     tused_overridden_date              date,

    -- audit fields
                                                     nomis_created_at                   timestamp(6) without time zone NOT NULL,
                                                     nomis_created_by                   varchar(32) NOT NULL,
                                                     nomis_modified_at                  timestamp(6) without time zone,
                                                     nomis_modified_by                  varchar(32),
                                                     nomis_recorded_at                  timestamp(0) without time zone,
                                                     nomis_recorded_by                  varchar(30),

                                                     CONSTRAINT legacy_sentence_calculations_pk
                                                         PRIMARY KEY (nomis_calculation_id),

                                                     CONSTRAINT legacy_sentence_calculations_request_fk
                                                         FOREIGN KEY (calculation_request_id)
                                                             REFERENCES calculation_request (id)
                                                             ON DELETE SET NULL
);

COMMENT ON COLUMN legacy_sentence_calculations.nomis_calculation_id IS E'Description: Source-assigned NOMIS calculation identity. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.prisoner_id IS E'Description: The prisoner identifier. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.booking_id IS E'Description: The booking identifier. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.crds_calculation_reference IS E'Description: Optional CRDS reference extracted from the source comment. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.calculation_request_id IS E'Description: Optional resolved relationship to a CRDS calculation request. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.calculation_date IS E'Description: The date of the calculation \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.comment_text IS E'Description: Free text field from NOMIS which may contain the calculation reference if it came from CRDS originally. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.calculation_reason_code IS E'Description: NOMIS reason code \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.hdced_calculated_date IS E'Description: HDCED either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.hdced_overridden_date IS E'Description: HDCED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.hdcad_calculated_date IS E'Description: HDCAD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.hdcad_overridden_date IS E'Description: HDCAD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.etd_calculated_date IS E'Description: ETD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.etd_overridden_date IS E'Description: ETD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.mtd_calculated_date IS E'Description: MTD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.mtd_overridden_date IS E'Description: MTD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ltd_calculated_date IS E'Description: LTD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ltd_overridden_date IS E'Description: LTD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ard_calculated_date IS E'Description: ARD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ard_overridden_date IS E'Description: ARD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.crd_calculated_date IS E'Description: CRD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.crd_overridden_date IS E'Description: CRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ped_calculated_date IS E'Description: PED either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ped_overridden_date IS E'Description: PED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.apd_calculated_date IS E'Description: APD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.apd_overridden_date IS E'Description: APD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.npd_calculated_date IS E'Description: NPD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.npd_overridden_date IS E'Description: NPD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.led_calculated_date IS E'Description: LED either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.led_overridden_date IS E'Description: LED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.sed_calculated_date IS E'Description: SED either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.sed_overridden_date IS E'Description: SED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.prrd_calculated_date IS E'Description: PRRD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.prrd_overridden_date IS E'Description: PRRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.tariff_calculated_date IS E'Description: TARIFF either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.tariff_overridden_date IS E'Description: TARIFF overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.dprrd_calculated_date IS E'Description: DPRRD either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.dprrd_overridden_date IS E'Description: DPRRD overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.ersed_overridden_date IS E'Description: ERSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.tersed_overridden_date IS E'Description: TERSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.rotl_overridden_date IS E'Description: ROTL overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.tused_calculated_date IS E'Description: TUSED either calculated by NOMIS or CRDS or manually entered in CRDS. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.tused_overridden_date IS E'Description: TUSED overridden date if source is from NOMIS, CRDS writes overridden dates into the calculated field. \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_created_at IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the created at time \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_created_by IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the created by user \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_modified_at IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the modified at time \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_modified_by IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the modified by user \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_recorded_at IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the recorded at time \nSource System: NOMIS ';
COMMENT ON COLUMN legacy_sentence_calculations.nomis_recorded_by IS E'Description: NOMIS audit field relating to modifications of this calculation. Records the recorded by user \nSource System: NOMIS ';

CREATE INDEX idx_legacy_sentence_calculations_prisoner_id ON legacy_sentence_calculations (prisoner_id);

CREATE INDEX idx_legacy_sentence_calculations_booking_id ON legacy_sentence_calculations (booking_id);

CREATE INDEX idx_legacy_sentence_calculations_request_id ON legacy_sentence_calculations (calculation_request_id);
