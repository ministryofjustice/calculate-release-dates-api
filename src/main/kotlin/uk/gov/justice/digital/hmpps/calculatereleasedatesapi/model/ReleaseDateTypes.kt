package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import java.time.temporal.ChronoUnit.MONTHS

class ReleaseDateTypes(
  val initialTypes: List<ReleaseDateType>,
  private val sentence: CalculableSentence,
  private val offender: Offender,
) {

  fun getReleaseDateTypes(): List<ReleaseDateType> {
    if (sentence.isCalculationInitialised()) {
      val underEighteenAtTimeOfRelease = offender.getAgeOnDate(sentence.sentenceCalculation.releaseDate) < INT_EIGHTEEN
      val lessThanTwelveMonths = sentence.durationIsLessThan(12, MONTHS)
      if (underEighteenAtTimeOfRelease && lessThanTwelveMonths && !sentence.isDto()) {
        val dates = initialTypes.toMutableList()
        dates -= ReleaseDateType.SLED
        dates -= ReleaseDateType.CRD
        dates += ReleaseDateType.SED
        dates += ReleaseDateType.ARD
        return dates
      }
    }
    return initialTypes
  }

  operator fun contains(releaseDateType: ReleaseDateType): Boolean {
    return getReleaseDateTypes().contains(releaseDateType)
  }

  companion object {
    private const val INT_EIGHTEEN = 18
  }
}
