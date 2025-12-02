-- calculation_outcome indexes for previous SLED query
CREATE INDEX idx_calculation_outcome_calculation_date_type ON calculation_outcome(calculation_date_type);
CREATE INDEX idx_calculation_outcome_outcome_date ON calculation_outcome(outcome_date);

-- calculation_request indexes for various queries on calculation by type, status and period, including previous SLED
CREATE INDEX idx_calculation_request_calculated_status ON calculation_request(calculation_status);
CREATE INDEX idx_calculation_request_calculated_type ON calculation_request(calculation_type);
CREATE INDEX idx_calculation_request_calculated_at ON calculation_request(calculated_at);
