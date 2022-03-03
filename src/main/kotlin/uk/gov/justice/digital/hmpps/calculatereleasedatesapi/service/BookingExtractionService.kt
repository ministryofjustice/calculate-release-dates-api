package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NCRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoSentencesProvidedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

@Service
class BookingExtractionService(
  val extractionService: SentencesExtractionService
) {

  fun extract(
    booking: Booking
  ): BookingCalculation {
    return when (booking.getAllExtractableSentences().size) {
      0 -> throw NoSentencesProvidedException("At least one sentence must be provided")
      1 -> extractSingle(booking)
      else -> {
        extractMultiple(booking)
      }
    }
  }

  private fun extractSingle(booking: Booking): BookingCalculation {
    val bookingCalculation = BookingCalculation()
    val sentence = booking.getAllExtractableSentences()[0]
    val sentenceCalculation = sentence.sentenceCalculation

    if (sentence.releaseDateTypes.contains(SLED)) {
      bookingCalculation.dates[SLED] = sentenceCalculation.expiryDate!!
    } else {
      bookingCalculation.dates[SED] = sentenceCalculation.expiryDate!!
    }

    bookingCalculation.dates[sentence.getReleaseDateType()] = sentenceCalculation.releaseDate!!

    if (sentenceCalculation.licenceExpiryDate != null &&
      sentenceCalculation.licenceExpiryDate != sentenceCalculation.expiryDate
    ) {
      bookingCalculation.dates[LED] = sentenceCalculation.licenceExpiryDate!!
    }

    if (sentenceCalculation.nonParoleDate != null) {
      bookingCalculation.dates[NPD] = sentenceCalculation.nonParoleDate!!
    }

    if (sentenceCalculation.topUpSupervisionDate != null) {
      bookingCalculation.dates[TUSED] = sentenceCalculation.topUpSupervisionDate!!
    }

    if (sentenceCalculation.homeDetentionCurfewEligibilityDate != null) {
      bookingCalculation.dates[HDCED] = sentenceCalculation.homeDetentionCurfewEligibilityDate!!
    }

    if (sentenceCalculation.notionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = sentenceCalculation.notionalConditionalReleaseDate!!
    }

    bookingCalculation.dates[ESED] = sentenceCalculation.unadjustedExpiryDate
    bookingCalculation.effectiveSentenceLength =
      getEffectiveSentenceLength(sentence.sentencedAt, sentenceCalculation.unadjustedExpiryDate)
    bookingCalculation.breakdownByReleaseDateType = sentenceCalculation.breakdownByReleaseDateType

    return bookingCalculation
  }

  private fun extractMultiple(booking: Booking): BookingCalculation {
    val breakdownByReleaseDateType: MutableMap<ReleaseDateType, ReleaseDateCalculationBreakdown> = mutableMapOf()
    val bookingCalculation = BookingCalculation()
    val sentences = booking.getAllExtractableSentences()
    val earliestSentenceDate = sentences.minOf { it.sentencedAt }

    val mostRecentSentenceByReleaseDate =
      extractionService.mostRecentSentence(sentences, SentenceCalculation::releaseDate)
    val mostRecentSentenceByExpiryDate =
      extractionService.mostRecentSentence(sentences, SentenceCalculation::expiryDate)

    val latestReleaseDate = mostRecentSentenceByReleaseDate.sentenceCalculation.releaseDate!!
    val latestExpiryDate = mostRecentSentenceByExpiryDate.sentenceCalculation.expiryDate!!

    val latestUnadjustedExpiryDate: LocalDate = extractionService.mostRecent(
      sentences, SentenceCalculation::unadjustedExpiryDate
    )

    val effectiveSentenceLength = getEffectiveSentenceLength(
      earliestSentenceDate,
      latestUnadjustedExpiryDate
    )

    val latestLicenseExpiryDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::licenceExpiryDate
    )

    val latestNonParoleDate: LocalDate? = extractManyNonParoleDate(sentences, latestReleaseDate)

    val latestHDCEDAndBreakdown =
      extractManyHomeDetentionCurfewEligibilityDate(
        sentences,
        effectiveSentenceLength,
        latestReleaseDate,
      )

    val latestTUSEDAndBreakdown = if (latestLicenseExpiryDate != null) {
      extractManyTopUpSuperVisionDate(sentences, latestLicenseExpiryDate)
    } else {
      null
    }

    val isReleaseDateConditional = extractManyIsReleaseConditional(
      mostRecentSentenceByReleaseDate.releaseDateTypes, mostRecentSentenceByExpiryDate.releaseDateTypes, latestReleaseDate, sentences, effectiveSentenceLength
    )

    val latestNotionalConditionalReleaseDate: LocalDate? = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::notionalConditionalReleaseDate
    )

    if (latestExpiryDate == latestLicenseExpiryDate) {
      bookingCalculation.dates[SLED] = latestExpiryDate
      breakdownByReleaseDateType[SLED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SLED]!!
    } else {
      bookingCalculation.dates[SED] = latestExpiryDate
      breakdownByReleaseDateType[SED] =
        mostRecentSentenceByExpiryDate.sentenceCalculation.breakdownByReleaseDateType[SED]!!
      if (latestLicenseExpiryDate != null) {
        bookingCalculation.dates[LED] = latestLicenseExpiryDate
      }
    }

    if (isReleaseDateConditional) {
      bookingCalculation.dates[CRD] = latestReleaseDate
      // PSI Example 16 results in a situation where the latest calculated sentence has ARD associated but isReleaseDateConditional here is deemed true.
      val releaseDateType = if (mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType.containsKey(CRD)) CRD else ARD
      breakdownByReleaseDateType[CRD] = mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType[releaseDateType]!!
    } else {
      bookingCalculation.dates[ARD] = latestReleaseDate
      breakdownByReleaseDateType[ARD] = mostRecentSentenceByReleaseDate.sentenceCalculation.breakdownByReleaseDateType[ARD]!!
    }

    if (latestNonParoleDate != null) {
      bookingCalculation.dates[NPD] = latestNonParoleDate
    }

    if (latestTUSEDAndBreakdown != null) {
      bookingCalculation.dates[TUSED] = latestTUSEDAndBreakdown.first
      breakdownByReleaseDateType[TUSED] = latestTUSEDAndBreakdown.second
    }

    if (latestHDCEDAndBreakdown != null) {
      bookingCalculation.dates[HDCED] = latestHDCEDAndBreakdown.first
      breakdownByReleaseDateType[HDCED] = latestHDCEDAndBreakdown.second
    }

    if (latestNotionalConditionalReleaseDate != null) {
      bookingCalculation.dates[NCRD] = latestNotionalConditionalReleaseDate
    }

    bookingCalculation.dates[ESED] = latestUnadjustedExpiryDate
    bookingCalculation.effectiveSentenceLength = effectiveSentenceLength

    bookingCalculation.breakdownByReleaseDateType = breakdownByReleaseDateType
    return bookingCalculation
  }

  private fun extractManyHomeDetentionCurfewEligibilityDate(
    sentences: List<ExtractableSentence>,
    effectiveSentenceLength: Period,
    latestAdjustedReleaseDate: LocalDate
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    return if (effectiveSentenceLength.years < 4) {
      val hdcedSentence = extractionService.mostRecentSentenceOrNull(
        sentences.filter { !latestAdjustedReleaseDate.isBefore(it.sentencedAt.plusDays(14)) }, SentenceCalculation::homeDetentionCurfewEligibilityDate
      )
      if (hdcedSentence != null) {
        hdcedSentence.sentenceCalculation.homeDetentionCurfewEligibilityDate!! to hdcedSentence.sentenceCalculation.breakdownByReleaseDateType[HDCED]!!
      } else null
    } else null
  }

  private fun getEffectiveSentenceLength(start: LocalDate, end: LocalDate): Period =
    Period.between(start, end.plusDays(1))

  private fun extractManyNonParoleDate(
    sentences: List<ExtractableSentence>,
    latestReleaseDate: LocalDate
  ): LocalDate? {

    val mostRecentNonParoleDate = extractionService.mostRecentOrNull(
      sentences, SentenceCalculation::nonParoleDate
    )
    return if (mostRecentNonParoleDate != null &&
      mostRecentNonParoleDate.isAfter(latestReleaseDate)
    ) {
      latestReleaseDate
    } else {
      null
    }
  }

  private fun extractManyTopUpSuperVisionDate(
    sentences: List<ExtractableSentence>,
    latestLicenseExpiryDate: LocalDate
  ): Pair<LocalDate, ReleaseDateCalculationBreakdown>? {
    val latestTUSEDSentence = sentences
      .filter { it.sentenceCalculation.topUpSupervisionDate != null }
      .maxByOrNull { it.sentenceCalculation.topUpSupervisionDate!! }

    return if (latestTUSEDSentence != null && latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!!.isAfter(latestLicenseExpiryDate)) {
      latestTUSEDSentence.sentenceCalculation.topUpSupervisionDate!! to latestTUSEDSentence.sentenceCalculation.breakdownByReleaseDateType[TUSED]!!
    } else null
  }

  private fun extractManyIsReleaseConditional(
    latestReleaseTypes: List<ReleaseDateType>,
    latestExpiryTypes: List<ReleaseDateType>,
    latestReleaseDate: LocalDate,
    sentences: List<ExtractableSentence>,
    effectiveSentenceLength: Period
  ): Boolean {
    val latestReleaseIsConditional = latestReleaseTypes.contains(CRD)
    val latestSentenceExpiryIsSED = latestExpiryTypes.contains(SED)

    val hasOraSentences = sentences.any { (it is Sentence) && it.isOraSentence() }
    val hasNonOraSentencesOfLessThan12Months = sentences.any { (it is Sentence) && !it.isOraSentence() && it.durationIsLessThan(12, ChronoUnit.MONTHS) }
    val mostRecentSentenceWithASed = extractionService.mostRecentSentenceOrNull(
      sentences, SentenceCalculation::expiryDate
    ) { it.releaseDateTypes.contains(SED) }
    val mostRecentSentenceWithASled = extractionService.mostRecentSentenceOrNull(
      sentences, SentenceCalculation::expiryDate
    ) { it.releaseDateTypes.contains(SLED) }

    //We have a mix of ora and non-ora sentences
    if (hasOraSentences && hasNonOraSentencesOfLessThan12Months && mostRecentSentenceWithASed != null && mostRecentSentenceWithASled != null && effectiveSentenceLength.years < FOUR) {
      if (!latestReleaseIsConditional) {
        return if (latestSentenceExpiryIsSED) {
          !latestReleaseDate.isAfter(mostRecentSentenceWithASled.sentenceCalculation.expiryDate)
        } else {
          true
        }
      }
    }
    return latestReleaseIsConditional
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val FOUR = 4L
  }
}
