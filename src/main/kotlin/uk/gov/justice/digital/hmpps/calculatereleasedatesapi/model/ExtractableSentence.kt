package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
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
    return LocalDateRange.of(
      sentencedAt,
      sentenceCalculation.unadjustedReleaseDate.minusDays(sentenceCalculation.calculatedTotalDeductedDays.toLong())
    )
  }
}
