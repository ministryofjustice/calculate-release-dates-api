package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

/**
 * Timeline events on the same date are handled in the order defined here.
 * This is important in some scenarios such as whether someone is sentenced on the same day as SDS legislation commencement.
 */
enum class TimelineCalculationType(val order: Int) {
  SIMPLE_SNAPSHOT(5),
  SDS_LEGISLATION_COMMENCEMENT(10),
  SDS_TRANCHE_ALLOCATION(20),
  SDS_LEGISLATION_AMENDMENT(30),
  SDS40_SNAPSHOT(35),
  PROGRESSION_MODEL_SNAPSHOT(36),
  SDS_TRANCHE_RECALCULATION(37),
  FTR56_TRANCHE(40),
  SENTENCED(50),
  EXTERNAL_MOVEMENT(60),
  ADDITIONAL_DAYS(70),
  RESTORATION_DAYS(80),
  UAL(90),
}
