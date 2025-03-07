package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.MismatchType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationType

interface BulkComparisonService {

  fun processPrisonComparison(comparison: Comparison, token: String)

  fun processFullCaseLoadComparison(comparison: Comparison, token: String)

  fun processManualComparison(comparison: Comparison, prisonerIds: List<String>, token: String)

  fun determineMismatchType(
    validationResult: ValidationResult,
    sentenceCalcDates: SentenceCalcDates?,
    sentenceAndOffenceWithReleaseArrangements: List<SentenceAndOffenceWithReleaseArrangements>,
  ): MismatchType {
    if (validationResult.messages.isEmpty()) {
      val datesMatch =
        doDatesMatch(validationResult.calculatedReleaseDates, sentenceCalcDates)
      return if (datesMatch) MismatchType.NONE else MismatchType.RELEASE_DATES_MISMATCH
    }

    val unsupportedSentenceType =
      validationResult.messages.any { it.code == ValidationCode.UNSUPPORTED_SENTENCE_TYPE || it.code.validationType == ValidationType.UNSUPPORTED_CALCULATION }
    if (unsupportedSentenceType) {
      return MismatchType.UNSUPPORTED_SENTENCE_TYPE
    }

    return MismatchType.VALIDATION_ERROR
  }

  private fun doDatesMatch(
    calculatedReleaseDates: CalculatedReleaseDates?,
    sentenceCalcDates: SentenceCalcDates?,
  ): Boolean {
    if (calculatedReleaseDates != null && calculatedReleaseDates.dates.isNotEmpty()) {
      val calculatedSentenceCalcDates = calculatedReleaseDates.toSentenceCalcDates()
      if (sentenceCalcDates != null) {
        return calculatedSentenceCalcDates.isSameComparableCalculatedDates(sentenceCalcDates)
      }
    }
    return true
  }

  fun getEstablishmentValueForComparisonPerson(comparison: Comparison, establishment: String?): String? {
    val establishmentValue = if (comparison.comparisonType != ComparisonType.MANUAL) {
      if (establishment == null || establishment == "") {
        comparison.prison!!
      } else {
        establishment
      }
    } else {
      null
    }
    return establishmentValue
  }
}
