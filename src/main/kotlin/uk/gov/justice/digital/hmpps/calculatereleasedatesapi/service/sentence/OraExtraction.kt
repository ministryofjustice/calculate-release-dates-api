package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BookingExtractionService.ConcurrentOraAndNonOraDetails
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.MONTHS

fun latestDateType(dateType: ReleaseDateType, sentences: List<CalculableSentence>) = sentences
  .filter { it.isCalculationInitialised() && it.releaseDateTypes.contains(dateType) }
  .maxByOrNull { it.sentenceCalculation.expiryDate }

fun hasOraAndNoneOra(sentences: List<CalculableSentence>) = sentences.any { (it is StandardDeterminateSentence) && it.isOraSentence() } &&
  sentences.any { (it is StandardDeterminateSentence) && !it.isOraSentence() && it.durationIsLessThan(12, MONTHS) }

fun oraAndNoneOraExtraction(
  latestReleaseTypes: List<ReleaseDateType>,
  latestExpiryTypes: List<ReleaseDateType>,
  latestReleaseDate: LocalDate,
  sentences: List<CalculableSentence>,
  effectiveSentenceLength: Period,
): ConcurrentOraAndNonOraDetails? {
  if (latestReleaseTypes.contains(CRD) || effectiveSentenceLength.years >= 4 || !hasOraAndNoneOra(sentences)) {
    return null
  }

  val mostRecentSentenceWithASed = latestDateType(SED, sentences)
  val mostRecentSentenceWithASled = latestDateType(SLED, sentences)

  if (mostRecentSentenceWithASed == null || mostRecentSentenceWithASled == null) {
    return null
  }

  return if (
    latestExpiryTypes.contains(SED) &&
    latestReleaseDate.isAfter(mostRecentSentenceWithASled.sentenceCalculation.expiryDate)
  ) {
    ConcurrentOraAndNonOraDetails(
      isReleaseDateConditional = false,
      canHaveLicenseExpiry = false,
    )
  } else {
    ConcurrentOraAndNonOraDetails(
      isReleaseDateConditional = true,
      canHaveLicenseExpiry = true,
    )
  }
}
