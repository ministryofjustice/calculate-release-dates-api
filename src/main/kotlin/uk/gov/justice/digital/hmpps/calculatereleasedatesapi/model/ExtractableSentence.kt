package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED

interface ExtractableSentence : CalculableSentence {

  @JsonIgnore
  fun getRangeOfSentenceBeforeAwardedDays(): LocalDateRange {
    /*
    When we're walking the timeline of the sentence, we want to use the NPD date rather than PED, otherwise we
    could falsely state that there is a gap in the booking timeline if the prisoner wasn't released at the PED.
    */
    val releaseDate = if (getReleaseDateType() === PED) {
      sentenceCalculation.nonParoleDate!!
    } else {
      sentenceCalculation.releaseDate!!
    }
    val releaseDateBeforeAda = releaseDate.minusDays(sentenceCalculation.calculatedTotalAwardedDays.toLong())

    return if (sentencedAt.isAfter(releaseDateBeforeAda)) {
      // The deducted days make this an immediate release sentence.
      LocalDateRange.of(
        sentencedAt,
        sentencedAt
      )
    } else {
      LocalDateRange.of(
        sentencedAt,
        releaseDateBeforeAda
      )
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
