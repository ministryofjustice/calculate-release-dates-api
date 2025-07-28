package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class ComparisonStatus {
  SETUP, // In setup creating the comparison record, getting prisoners and queuing each message
  PROCESSING, // Processing each calculation required
  COMPLETED, // Processing complete
  ERROR, // Unable to complete due to error in setup
}
