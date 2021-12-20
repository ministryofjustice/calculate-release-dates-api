package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED

interface ExtractableSentence : SentenceTimeline {
  var releaseDateTypes: List<ReleaseDateType>
  var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  fun getReleaseDateType(): ReleaseDateType {
    return if (releaseDateTypes.contains(PED))
      PED else if (sentenceCalculation.isReleaseDateConditional)
      CRD else
      ARD
  }

  @JsonIgnore
  fun getDateRangeFromStartToReleaseWithoutDaysAwarded(): LocalDateRange {

    log.info(
      "getDateRangeFromStartToReleaseWithoutDaysAwarded: Comparing sentenced at {} with release date {}",
      sentencedAt,
      sentenceCalculation.unadjustedReleaseDate.minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong()))

    /* TODO: The following has been added to handle a scenario caused by
        using the aggregated rather than the sentence level adjustment.
        This should be removed when that feature is implemented */

    return if (sentencedAt.isAfter( sentenceCalculation.unadjustedReleaseDate.minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong()))) {
      /*
      if the adjustments applied the sentence mean that the sentence would be negative
      (because of long adjustments on another sentence) then return
      a date range with the sentence date as the start and end date.
      */
      LocalDateRange.of(
        sentencedAt,
        sentencedAt
      )
    } else {
      LocalDateRange.of(
        sentencedAt,
        sentenceCalculation.unadjustedReleaseDate.minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
      )
    }


  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
