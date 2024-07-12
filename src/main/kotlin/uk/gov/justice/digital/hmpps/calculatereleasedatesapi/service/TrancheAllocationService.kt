package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

@Service
class TrancheAllocationService(
  @Autowired
  private val trancheOne: TrancheOne,

  @Autowired
  private val trancheTwo: TrancheTwo,
) {

  fun calculateTranche(calculationResult: CalculationResult, booking: Booking): SDSEarlyReleaseTranche {
    // List of sentences allowable for SDS early release
    val sdsEarlyReleaseSentences =
      booking.sentences.filter { sentence -> sentence.identificationTrack.equals(SentenceIdentificationTrack.SDS_EARLY_RELEASE) }

    var resultTranche: SDSEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0

    if (sdsEarlyReleaseSentences.isNotEmpty()) {
      if (trancheOne.isBookingApplicableForTrancheCriteria(calculationResult, booking)) {
        resultTranche = SDSEarlyReleaseTranche.TRANCHE_1
      } else if (trancheTwo.isBookingApplicableForTrancheCriteria(calculationResult, booking)) {
        resultTranche = SDSEarlyReleaseTranche.TRANCHE_2
      }
    }

    return resultTranche
  }
}
