package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class CalculationRule {
  HDCED_GE_12W_LT_18M,
  HDCED_GE_18M_LT_4Y,
  HDCED_MINIMUM_14D,
  TUSED_LICENCE_PERIOD_LT_1Y,
  LED_CONSEC_ORA_AND_NON_ORA,
}
