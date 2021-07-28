package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfileCalculation

@Service
class CalculationService(
  private val offenderSentenceProfileCalculationService: OffenderSentenceProfileCalculationService
) {

  fun calculate(offenderSentenceProfile: OffenderSentenceProfile): OffenderSentenceProfileCalculation {
    var workingOffenderSentenceProfile: OffenderSentenceProfile = offenderSentenceProfile.copy()

    // identify the types of the sentences
    workingOffenderSentenceProfile =
      offenderSentenceProfileCalculationService
        .identify(workingOffenderSentenceProfile)

    // calculate the dates within the sentences (Generate initial sentence calculations)
    workingOffenderSentenceProfile =
      offenderSentenceProfileCalculationService
        .calculate(workingOffenderSentenceProfile)

    // aggregate the types of the sentences
    workingOffenderSentenceProfile =
      offenderSentenceProfileCalculationService
        .aggregate(workingOffenderSentenceProfile)

    // apply any rules to calculate the dates
    return offenderSentenceProfileCalculationService
      .extract(workingOffenderSentenceProfile)
  }
}
