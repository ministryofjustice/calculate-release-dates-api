package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.UNSUPPORTED_CALCULATION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.UNSUPPORTED_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.VALIDATION

@Schema(description = "Validation code details")
enum class ValidationCode(val message: String, val validationType: ValidationType = VALIDATION) {
  ADJUSTMENT_AFTER_RELEASE_ADA("The from date for Additional days awarded (ADA) should be the date of the adjudication hearing."),
  ADJUSTMENT_AFTER_RELEASE_RADA("The from date for Restored additional days awarded (RADA) must be the date the additional days were remitted."),
  ADJUSTMENT_FUTURE_DATED_ADA("The from date for Additional days awarded (ADA) should be the date of the adjudication hearing."),
  ADJUSTMENT_FUTURE_DATED_RADA("The from date for Restored additional days awarded (RADA) must be the date the additional days were remitted."),
  ADJUSTMENT_FUTURE_DATED_UAL("The from date for Unlawfully at Large (UAL) must be the first day the prisoner was deemed UAL."),
  A_FINE_SENTENCE_CONSECUTIVE("A sentence is consecutive to a default term.", UNSUPPORTED_CALCULATION),
  A_FINE_SENTENCE_CONSECUTIVE_TO(
    "A default term is consecutive to another default term or sentence.",
    UNSUPPORTED_CALCULATION
  ),
  A_FINE_SENTENCE_MISSING_FINE_AMOUNT("Court case %s count %s must include a fine amount."),
  A_FINE_SENTENCE_WITH_PAYMENTS("Any of the fine amount for a default term has been paid.", UNSUPPORTED_CALCULATION),
  CUSTODIAL_PERIOD_EXTINGUISHED_REMAND("The release date cannot be before the sentence date. Go back to NOMIS and reduce the amount of remand entered"),
  CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL("The release date cannot be before the sentence date. Go back to NOMIS and reduce the amount of tagged bail entered"),
  DTO_RECALL("DTO Holding Message"),
  EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s count %s is invalid for the sentence date entered."),
  EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR("Court case %s count %s must have a licence term of at least one year."),
  EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS("Court case %s count %s must have a licence term that does not exceed 8 years."),
  FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER("This calculation must have either a 14 or a 28 day fixed term recall sentence type."),
  FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28("You cannot have 28 days recall for a 14 day fixed term recall sentence type."),
  FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14("You cannot have 14 days recall for a 28 day fixed term recall sentence type."),
  FTR_14_DAYS_SENTENCE_GE_12_MONTHS("The sentence length is 12 months or more, so the fixed term recall should be 28 days."),
  FTR_14_DAYS_AGGREGATE_GE_12_MONTHS("The aggregate sentence length for the consecutive sentences is 12 months or more, so the fixed term recall should be 28 days."),
  FTR_28_DAYS_SENTENCE_LT_12_MONTHS("The sentence is less than 12 months so the fixed term recall should be 14 days."),
  FTR_28_DAYS_AGGREGATE_LT_12_MONTHS("The aggregate sentence length for the consecutive sentences is less than 12 months, so the fixed term recall should be 14 days."),
  FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS("The sentence length is 12 months or more, so the fixed term sentence type should be 28 days."),
  FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS("The aggregate sentence length for the consecutive sentences is 12 months or more, so the fixed term sentence type should be 28 days."),
  FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS("The sentence length is less than 12 months, so the fixed term sentence type should be 14 days."),
  FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS("The aggregate sentence length for the consecutive sentences is less than 12 months, so the fixed term sentence type should be 14 days."),
  LASPO_AR_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s count %s is invalid for the sentence date entered."),
  MORE_THAN_ONE_IMPRISONMENT_TERM("Court case %s count %s must only have one imprisonment term."),
  MORE_THAN_ONE_LICENCE_TERM("Court case %s count %s must only have one licence term."),
  MULTIPLE_SENTENCES_CONSECUTIVE_TO("Court case %s count %s has multiple sentences that have been made consecutive to it. A sentence should only have one other sentence consecutive to it."),
  OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE("The offence date range for court case %s count %s must be before the sentence date."),
  OFFENCE_DATE_AFTER_SENTENCE_START_DATE("The offence date for court case %s count %s must be before the sentence date."),
  OFFENCE_MISSING_DATE("Court case %s count %s must include an offence date."),
  PRISONER_SUBJECT_TO_PTD("Prisoner has PTD alert after PCSC commencement date, this is unsupported"),
  REMAND_FROM_TO_DATES_REQUIRED("Remand periods must have a from and to date."),
  REMAND_OVERLAPS_WITH_REMAND("Remand time can only be added once, it can cannot overlap with other remand dates."),
  REMAND_OVERLAPS_WITH_SENTENCE("Remand time cannot be credited when a custodial sentence is being served."),
  SEC236A_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s count %s is invalid for the sentence date entered."),
  SEC_91_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s count %s is invalid for the sentence date entered."),
  SENTENCE_HAS_MULTIPLE_TERMS("Court case %s count %s must only have one term in NOMIS."),
  SENTENCE_HAS_NO_IMPRISONMENT_TERM("Court case %s count %s must include an imprisonment term."),
  SENTENCE_HAS_NO_LICENCE_TERM("Court case %s count %s must include a licence term."),
  SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s count %s is invalid for the sentence date entered."),
  SOPC_LICENCE_TERM_NOT_12_MONTHS("Court case %s count %s must include a licence term of 12 months or 1 year."),
  UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE(
    "There is a Lawfully at Large (LAL) adjustment on the associated booking",
    UNSUPPORTED_CALCULATION
  ),
  UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION(
    "There is a Special Remission (SR) adjustment on the associated booking",
    UNSUPPORTED_CALCULATION
  ),
  UNSUPPORTED_SENTENCE_TYPE("Unsupported sentence type %s %s", UNSUPPORTED_SENTENCE),
  ZERO_IMPRISONMENT_TERM("Court case %s count %s must include an imprisonment term greater than zero."),
  UNSUPPORTED_CALCULATION_DTO_WITH_RECALL("Unsupported calculation - DTO and recall"),
}
