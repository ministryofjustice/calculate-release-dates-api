package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.CONCURRENT_CONSECUTIVE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.MANUAL_ENTRY_JOURNEY_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.SUSPENDED_OFFENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.UNSUPPORTED_CALCULATION
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType.UNSUPPORTED_OFFENCE
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
    UNSUPPORTED_CALCULATION,
  ),
  DTO_RECALL("A DTO SEC104 or SEC105 has a breach term.", UNSUPPORTED_CALCULATION), // can still be used when parsing historic comparisons
  A_FINE_SENTENCE_MISSING_FINE_AMOUNT("Court case %s NOMIS line reference %s must include a fine amount."),
  A_FINE_SENTENCE_WITH_PAYMENTS("Any of the fine amount for a default term has been paid.", UNSUPPORTED_CALCULATION),
  CUSTODIAL_PERIOD_EXTINGUISHED_REMAND("The release date cannot be before the sentence date. Go back to NOMIS and reduce the amount of remand entered"),
  CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL("The release date cannot be before the sentence date. Go back to NOMIS and reduce the amount of tagged bail entered"),
  DTO_CONSECUTIVE_TO_SENTENCE("A DTO is consecutive to a sentence type that is not a DTO", UNSUPPORTED_CALCULATION),
  DTO_HAS_SENTENCE_CONSECUTIVE_TO_IT("A non-DTO sentence is consecutive to a DTO.", UNSUPPORTED_CALCULATION),
  EDS18_EDS21_EDSU18_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s NOMIS line reference %s is invalid for the sentence date entered."),
  EDS_LICENCE_TERM_LESS_THAN_ONE_YEAR("Court case %s NOMIS line reference %s must have a licence term of at least one year."),
  EDS_LICENCE_TERM_MORE_THAN_EIGHT_YEARS("Court case %s NOMIS line reference %s must have a licence term that does not exceed 8 years."),
  FTR_SENTENCES_CONFLICT_WITH_EACH_OTHER("This calculation must have either a 14 or a 28 day fixed term recall sentence type."),
  FTR_TYPE_14_DAYS_BUT_LENGTH_IS_28("You cannot have 28 days recall for a 14 day fixed term recall sentence type."),
  FTR_TYPE_28_DAYS_BUT_LENGTH_IS_14("You cannot have 14 days recall for a 28 day fixed term recall sentence type."),
  FTR_14_DAYS_SENTENCE_GE_12_MONTHS("The sentence length is 12 months or more, so the fixed term recall should be 28 days."),
  FTR_14_DAYS_AGGREGATE_GE_12_MONTHS("The aggregate sentence length for the consecutive sentences is 12 months or more, so the fixed term recall should be 28 days."),
  FTR_28_DAYS_SENTENCE_LT_12_MONTHS("The sentence is less than 12 months so the fixed term recall should be 14 days."),
  FTR_28_DAYS_AGGREGATE_LT_12_MONTHS("The aggregate sentence length for the consecutive sentences is less than 12 months, so the fixed term recall should be 14 days."),
  FTR_TYPE_14_DAYS_SENTENCE_GE_12_MONTHS("The sentence length is 12 months or more, so the fixed term sentence type should be 28 days."),
  FTR_TYPE_14_DAYS_AGGREGATE_GE_12_MONTHS("The aggregate sentence length for the consecutive sentences is 12 months or more, so the fixed term sentence type should be 28 days."),
  FTR_TYPE_14_DAYS_SENTENCE_GT_12_MONTHS("<h4>Incorrect recall term</h4><p>A 14-day fixed term recall has been selected.</p><p>Based on the sentence information, this should be a 28-day fixed term recall.</p><p>Change the fixed term recall to 28 days.</p><br/>"),
  FTR_TYPE_14_DAYS_SENTENCE_GAP_GT_14_DAYS("<h4>Incorrect recall term</h4><p>A 14-day fixed term recall has been selected.</p><p>Based on the sentence information, this should be a 28-day fixed term recall.</p><p>Change the fixed term recall to 28 days.</p><br/>"),
  FTR_TYPE_28_DAYS_SENTENCE_GAP_LT_14_DAYS("<h4>Incorrect recall term</h4><p>A 28-day fixed term recall has been selected.</p><p>Based on the sentence information, this should be a 14-day fixed term recall.</p><p>Change the fixed term recall to 14 days.</p><br/>"),
  FTR_TYPE_28_DAYS_SENTENCE_LT_12_MONTHS("The sentence length is less than 12 months, so the fixed term sentence type should be 14 days."),
  FTR_TYPE_28_DAYS_AGGREGATE_LT_12_MONTHS("The aggregate sentence length for the consecutive sentences is less than 12 months, so the fixed term sentence type should be 14 days."),
  FTR_TYPE_48_DAYS_OVERLAPPING_SENTENCE("Unsupported FTR48 calculation", MANUAL_ENTRY_JOURNEY_REQUIRED),
  LASPO_AR_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s NOMIS line reference %s is invalid for the sentence date entered."),
  MORE_THAN_ONE_IMPRISONMENT_TERM("Court case %s NOMIS line reference %s must only have one imprisonment term."),
  MORE_THAN_ONE_LICENCE_TERM("Court case %s NOMIS line reference %s must only have one licence term."),
  MULTIPLE_SENTENCES_CONSECUTIVE_TO("Court case %s NOMIS line reference %s has multiple sentences that have been made consecutive to it. A sentence should only have one other sentence consecutive to it."),
  OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE("The offence date range for court case %s NOMIS line reference %s must be before the sentence date."),
  OFFENCE_DATE_AFTER_SENTENCE_START_DATE("The offence date for court case %s NOMIS line reference %s must be before the sentence date."),
  OFFENCE_MISSING_DATE("Court case %s NOMIS line reference %s must include an offence date."),
  PRISONER_SUBJECT_TO_PTD("Prisoner has PTD alert after PCSC commencement date, this is unsupported"),
  REMAND_FROM_TO_DATES_REQUIRED("Remand periods must have a from and to date."),
  REMAND_OVERLAPS_WITH_REMAND("Remand time can only be added once, it can cannot overlap with other remand dates."),
  REMAND_OVERLAPS_WITH_SENTENCE("Remand time cannot be credited when a custodial sentence is being served."),
  SEC236A_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s NOMIS line reference %s is invalid for the sentence date entered."),
  SEC_91_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s NOMIS line reference %s is invalid for the sentence date entered."),
  SENTENCE_HAS_MULTIPLE_TERMS("Court case %s NOMIS line reference %s must only have one term in NOMIS."),
  SENTENCE_HAS_NO_IMPRISONMENT_TERM("Court case %s NOMIS line reference %s must include an imprisonment term."),
  SENTENCE_HAS_NO_LICENCE_TERM("Court case %s NOMIS line reference %s must include a licence term."),
  SOPC18_SOPC21_SENTENCE_TYPE_INCORRECT("The sentence type for court case %s NOMIS line reference %s is invalid for the sentence date entered."),
  SOPC_LICENCE_TERM_NOT_12_MONTHS("Court case %s NOMIS line reference %s must include a licence term of 12 months or 1 year."),
  SDS_TORERA_EXCLUSION("The calculation includes SDS TORERA offences."),
  SOPC_TORERA_EXCLUSION("The calculation includes SOPC TORERA offences."),
  UNSUPPORTED_ADJUSTMENT_LAWFULLY_AT_LARGE(
    "A Lawfully at large (LAL) adjustment has been recorded.",
    UNSUPPORTED_CALCULATION,
  ),
  UNSUPPORTED_ADJUSTMENT_SPECIAL_REMISSION(
    "There is a Special Remission (SR) adjustment on the associated booking",
    UNSUPPORTED_CALCULATION,
  ),
  UNSUPPORTED_ADJUSTMENT_TIME_SPENT_IN_CUSTODY_ABROAD(
    "A Time spent in custody abroad (TCA) adjustment has been recorded.",
    UNSUPPORTED_CALCULATION,
  ),
  UNSUPPORTED_ADJUSTMENT_TIME_SPENT_AS_AN_APPEAL_APPLICANT(
    "A Time spent as an appeal applicant (TSA) adjustment has been recorded.",
    UNSUPPORTED_CALCULATION,
  ),
  UNSUPPORTED_DTO_RECALL_SEC104_SEC105("A detention and training order has a SEC104 or SEC105 breach term.", UNSUPPORTED_CALCULATION),
  UNSUPPORTED_SENTENCE_TYPE("%s", UNSUPPORTED_SENTENCE),
  ZERO_IMPRISONMENT_TERM("Court case %s NOMIS line reference %s must include an imprisonment term greater than zero."),
  UNSUPPORTED_CALCULATION_DTO_WITH_RECALL("The calculation includes DTO and recalls.", UNSUPPORTED_CALCULATION),
  PRE_PCSC_DTO_WITH_ADJUSTMENT("If a Detention and training order (DTO) has a sentence date before 28 June 2022, %s cannot be applied."),
  BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE("A BOTUS licence is consecutive with another sentence type.", UNSUPPORTED_CALCULATION),
  BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE("A BOTUS licence is concurrent/consecutive with another sentence type", UNSUPPORTED_CALCULATION),
  UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE("The calculation includes unsupported recalls for SDS40.", MANUAL_ENTRY_JOURNEY_REQUIRED),
  UNSUPPORTED_SDS40_CONSECUTIVE_SDS_BETWEEN_TRANCHE_COMMENCEMENTS(
    "The SDS40 calculation includes unsupported SDS for tranche 2 prisoners, who have been sentenced between tranche commencement dates.",
    MANUAL_ENTRY_JOURNEY_REQUIRED,
  ),
  UNSUPPORTED_OFFENCE_ENCOURAGING_OR_ASSISTING(
    "Any offences that include the inchoate ‘Encouraging/ Assisting’ should be recorded as the underlying act, ending in the letter ‘e’.\n" +
      "For example, ‘Encouraging/Assisting’ a Rape SX03001 would be SX03001E.",
    UNSUPPORTED_OFFENCE,
  ),
  UNSUPPORTED_GENERIC_CONSPIRACY_OFFENCE(
    "The offence code CL77036 is a generic conspiracy offence and should not be used.\n" +
      "Any offences that include the inchoate 'Conspiracy' must be recorded as the underlying act, ending in the letter 'C'\n" +
      "For example, Conspiracy to Bribe BA10010 would be BA10010C.",
    UNSUPPORTED_OFFENCE,
  ),

  UNSUPPORTED_BREACH_97(
    "Breaches of restraining orders committed on or after 01 December 2020 must be sentenced under the 2020 Sentencing Act.\n" +
      "Go to NOMIS and change the offence code from PH97003 to SE20002.",
    UNSUPPORTED_OFFENCE,
  ),

  UNSUPPORTED_SUSPENDED_OFFENCE("Replace this offence in NOMIS with the original offence they were sentenced for, then reload this page.", SUSPENDED_OFFENCE),
  FTR_NO_RETURN_TO_CUSTODY_DATE("The Fixed term recall must have a 'return to custody' date"),
  NO_SENTENCES("Prisoner has no sentences"),
  UNABLE_TO_DETERMINE_SHPO_RELEASE_PROVISIONS(
    "The calculation requires release provisions for SHPO SX03, which cannot be determined by the service.",
    MANUAL_ENTRY_JOURNEY_REQUIRED,
  ),
  SE2020_INVALID_OFFENCE_DETAIL(
    "Offence %s falls under the Sentencing Act 2020. The offence date entered is before this offence became law. \n" +
      " Any offences under this act must be on or after 01 December 2020.\n Change the offence date or choose a relevant offence.",
  ),
  SE2020_INVALID_OFFENCE_COURT_DETAIL("Court case %s NOMIS line reference %s offence date is before the offence became law. Change the offence date or choose a relevant offence."),
  REMAND_ON_OR_AFTER_SENTENCE_DATE("The 'From' or 'To' date of the remand period must be earlier than the sentence date for court case %s NOMIS line reference %s."),
  DATES_MISSING_REQUIRED_TYPE("You cannot select a %s and a %s without a %s"),
  DATES_PAIRINGS_INVALID("%s and %s cannot be selected together"),
  CONCURRENT_CONSECUTIVE_SENTENCES_DURATION("%s years %s months %s weeks %s days", CONCURRENT_CONSECUTIVE),
  CONCURRENT_CONSECUTIVE_SENTENCES_NOTIFICATION("More than one sentence consecutive to the same sentence", CONCURRENT_CONSECUTIVE),
  CONSECUTIVE_SENTENCE_WITH_MULTIPLE_OFFENCES("Sentence with multiple offences is consecutive to another sentence"),
  BROKEN_CONSECUTIVE_CHAINS("You cannot have a sentence consecutive to an inactive sentence."),
  RECALL_MISSING_REVOCATION_DATE("An active recall sentence is present with no associated court event with a \"Recall to prison\" court outcome."),
}
