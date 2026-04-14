package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

/**
 * Timeline events on the same date are handled in the order defined here.
 * This is important in some scenarios such as whether someone is sentenced on the same day as SDS legislation commencement.
 */
enum class TimelineCalculationType(val order: Int) {
  SDS_LEGISLATION_COMMENCEMENT(10),
  EARLY_RELEASE_TRANCHE(20),
  SDS_LEGISLATION_AMENDMENT(30),
  FTR56_TRANCHE(40),
  EXTERNAL_MOVEMENT(50),
  SENTENCED(60),
  ADDITIONAL_DAYS(70),
  RESTORATION_DAYS(80),
  UAL(90),
}
