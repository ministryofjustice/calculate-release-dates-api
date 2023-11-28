CREATE TABLE calculation_reason
(
    id           INTEGER PRIMARY KEY,
    rank         INTEGER NOT NULL, -- Determines the order on the page, less than zero will not be displayed.
    display_name VARCHAR(40),
    is_other     BOOLEAN DEFAULT FALSE
);

ALTER TABLE calculation_request
    ADD COLUMN reason_for_calculation INT DEFAULT -1 REFERENCES calculation_reason (id);
ALTER TABLE calculation_request
    ADD COLUMN other_reason_for_calculation VARCHAR(40);

UPDATE calculation_request
SET reason_for_calculation = -1;

INSERT INTO calculation_reason (id, rank, display_name)
VALUES (1, 10, 'Initial calculation'),
       (2, 20, 'Transfer check'),
       (3, 30, 'Lodged warrant'),
       (4, 40, 'ADA - Additional Days Awarded'),
       (5, 50, 'RADA - Restoration of Added Days Awarded'),
       (6, 60, 'Recording a non-calculated date'),
       (7, 70, 'Further sentences and terms'),
       (8, 80, '14 day check'),
       (9, 90, '2 day check'),
       (10, 100, 'Appeal decision'),
       (-1, -1, 'Reason for calculation not provided');

INSERT INTO calculation_reason (id, rank, display_name, is_other)
VALUES (11, 110, 'Other', true);