package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
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
  @Autowired
  private val trancheConfiguration: SDS40TrancheConfiguration,
) {

  fun calculateTranche(calculationResult: CalculationResult, booking: Booking): SDSEarlyReleaseTranche {
    // Exclude any sentences where sentencing was after T1 commencement - CRS-2126
    // For the purposes of tranching any SDS is considered, Early release track is predicated on NOT having any exclusions
    //
    // Recalls sentenced before T1 commencement CAN NOT be recall for a SDS40 sentence, however a full implementation
    //
    // That looks at movements and date of release to determine release conditions applicable is yet to be implemented.
    val sentencesConsideredForTrancheRules =
      booking.sentences.filter { sentence ->
        (
          sentence.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE ||
            sentence.identificationTrack == SentenceIdentificationTrack.SDS_STANDARD_RELEASE
          ) &&
          (!sentence.isRecall() && sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate))
      }
        .filter { it.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate) }

    var resultTranche: SDSEarlyReleaseTranche = SDSEarlyReleaseTranche.TRANCHE_0

    sentencesConsideredForTrancheRules.takeIf { it.isNotEmpty() }?.let {
      val sentencesFromBooking = booking.getAllExtractableSentences()
      resultTranche = when {
        sentencesFromBooking.isEmpty() -> SDSEarlyReleaseTranche.TRANCHE_0
        trancheOne.isBookingApplicableForTrancheCriteria(
          calculationResult,
          sentencesFromBooking,
        ) -> SDSEarlyReleaseTranche.TRANCHE_1

        trancheTwo.isBookingApplicableForTrancheCriteria(
          calculationResult,
          sentencesFromBooking,
        ) -> SDSEarlyReleaseTranche.TRANCHE_2

        else -> resultTranche
      }
    }
    return resultTranche
  }
}
