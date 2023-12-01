CREATE TABLE calculation_reason
(
    id           SERIAL
        CONSTRAINT calculation_reason_pk PRIMARY KEY,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    is_other     BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR(40)
);

ALTER TABLE calculation_request
    ADD COLUMN reason_for_calculation INT REFERENCES calculation_reason (id);
ALTER TABLE calculation_request
    ADD COLUMN other_reason_for_calculation VARCHAR(40);

INSERT INTO calculation_reason (display_name)
VALUES ('Initial calculation'),
       ('Transfer check'),
       ('Lodged warrant'),
       ('ADA - Additional Days Awarded'),
       ('RADA - Restoration of Added Days Awarded'),
       ('Recording a non-calculated date'),
       ('Further sentences and terms'),
       ('14 day check'),
       ('2 day check'),
       ('Appeal decision');

INSERT INTO calculation_reason (display_name, is_other)
VALUES ('Other', true);

INSERT INTO calculation_reason (display_name, is_active)
VALUES ('Reason for calculation not provided', false);

UPDATE calculation_request
SET reason_for_calculation = (SELECT id
                              FROM calculation_reason
                              WHERE display_name = 'Reason for calculation not provided')
WHERE reason_for_calculation IS NULL;
