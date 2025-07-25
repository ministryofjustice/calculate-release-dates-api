package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

enum class CalculationRule {
  HDCED_GE_MIN_PERIOD_LT_MIDPOINT,
  HDCED_GE_MIDPOINT_LT_MAX_PERIOD,
  HDCED_MINIMUM_CUSTODIAL_PERIOD,
  HDC_180,
  HDCED_ADJUSTED_TO_365_COMMENCEMENT,
  TUSED_LICENCE_PERIOD_LT_1Y,
  LED_CONSEC_ORA_AND_NON_ORA,
  UNUSED_ADA,
  IMMEDIATE_RELEASE,
  PED_EQUAL_TO_LATEST_NON_PED_CONDITIONAL_RELEASE,
  PED_EQUAL_TO_LATEST_NON_PED_ACTUAL_RELEASE,
  HDCED_ADJUSTED_TO_CONCURRENT_CONDITIONAL_RELEASE,
  HDCED_ADJUSTED_TO_CONCURRENT_ACTUAL_RELEASE,
  HDCED_ADJUSTED_TO_CONCURRENT_PRRD,
  ERSED_MAX_PERIOD,
  ERSED_MIN_EFFECTIVE_DATE,
  ERSED_ADJUSTED_TO_CONCURRENT_TERM,
  ERSED_BEFORE_SENTENCE_DATE,
  ERSED_ADJUSTED_TO_MTD,
  ERSED_ADJUSTED_TO_ERS30_COMMENCEMENT,
  SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_1_COMMENCEMENT,
  SDS_EARLY_RELEASE_ADJUSTED_TO_TRANCHE_2_COMMENCEMENT,
  SDS_STANDARD_RELEASE_APPLIES,
  SDS_EARLY_RELEASE_APPLIES,
  ADJUSTED_AFTER_TRANCHE_COMMENCEMENT,
  BOTUS_LATEST_TUSED_USED,
}
