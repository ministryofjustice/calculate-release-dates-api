package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class ReleaseDateType(
  val description: String,
  val allowsWeekendAdjustment: Boolean = false,
  val isEarlyReleaseHintType: Boolean = false,
  val isStandardReleaseHintType: Boolean = false,
) {
  CRD("Conditional release date", allowsWeekendAdjustment = true, isEarlyReleaseHintType = true),
  LED("Licence expiry date"),
  SED("Sentence expiry date"),
  NPD("Non-parole date"),
  ARD("Automatic release date", allowsWeekendAdjustment = true, isEarlyReleaseHintType = true),
  TUSED("Top up supervision expiry date"),
  PED("Parole eligibility date", allowsWeekendAdjustment = true, isEarlyReleaseHintType = true, isStandardReleaseHintType = true),
  SLED("Sentence and licence expiry date"),
  HDCED("Home detention curfew eligibility date", allowsWeekendAdjustment = true, isEarlyReleaseHintType = true, isStandardReleaseHintType = true),
  NCRD("Notional conditional release date"),
  ETD("Early transfer date", allowsWeekendAdjustment = true),
  MTD("Mid transfer date", allowsWeekendAdjustment = true),
  LTD("Late transfer date", allowsWeekendAdjustment = true),
  DPRRD("Detention and training order post recall release date"),
  PRRD("Post recall release date", allowsWeekendAdjustment = true),
  ESED("Effective sentence end date"),
  ERSED("Early removal scheme eligibility date", isEarlyReleaseHintType = true, isStandardReleaseHintType = true),
  TERSED("Tariff-expired removal scheme eligibility date"),
  APD("Approved parole date"),
  HDCAD("Home detention curfew approved date"),
  None("None of the above dates apply"),
  Tariff("known as the Tariff expiry date"),
  ROTL("Release on temporary licence"),
  HDCED4PLUS("HDCED4+"), // *DO NOT REMOVE* - needed for legacy calculations only
}

// Used for interim calculations (these are not persisted), e.g. when the result of both types is used to determine the final HDCED
enum class InterimHdcCalcType() {
  HDCED_PRE_365_RULES,
  HDCED_POST_365_RULES,
}
