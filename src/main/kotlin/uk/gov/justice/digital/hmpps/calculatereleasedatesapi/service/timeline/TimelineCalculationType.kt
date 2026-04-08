package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

/**
 * Note that the order of elements is important as timeline events on the same date are handled in the order of this enum.
 */
enum class TimelineCalculationType {
  SDS_LEGISLATION_COMMENCEMENT,
  EARLY_RELEASE_TRANCHE,
  SDS_LEGISLATION_AMENDMENT,
  FTR56_TRANCHE,
  EXTERNAL_MOVEMENT,
  SENTENCED,
  ADDITIONAL_DAYS,
  RESTORATION_DAYS,
  UAL,
}
