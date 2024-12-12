package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class ReleaseDateType(val description: String) {
  CRD("Conditional release date"),
  LED("Licence expiry date"),
  SED("Sentence expiry date"),
  NPD("Non-parole date"),
  ARD("Automatic release date"),
  TUSED("Top up supervision expiry date"),
  PED("Parole eligibility date"),
  SLED("Sentence and licence expiry date"),
  HDCED("Home detention curfew eligibility date"),
  NCRD("Notional conditional release date"),
  ETD("Early transfer date"),
  MTD("Mid transfer date"),
  LTD("Late transfer date"),
  DPRRD("Detention and training order post recall release date"),
  PRRD("Post recall release date"),
  ESED("Effective sentence end date"),
  ERSED("Early removal scheme eligibility date"),
  TERSED("Tariff-expired removal scheme eligibility date"),
  APD("Approved parole date"),
  HDCAD("Home detention curfew approved date"),
  None("None of the above dates apply"),
  Tariff("known as the Tariff expiry date"),
  ROTL("Release on temporary licence"),
  HDCED365("HDCED Calculation where max time is 365 Days aka HDC-12"), // will become HDCED after 23 Jun 2025
}
