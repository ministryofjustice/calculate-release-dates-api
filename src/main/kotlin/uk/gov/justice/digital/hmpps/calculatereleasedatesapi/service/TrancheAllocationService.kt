package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

@Service
class TrancheAllocationService(
  @Autowired
  private val tranche: Tranche,
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
    val sentencesConsideredForTrancheRules = getSentencesForTrancheRules(booking)

    return when {
      sentencesConsideredForTrancheRules.isEmpty() -> SDSEarlyReleaseTranche.TRANCHE_0
      isApplicableForTranche1(calculationResult, booking) -> SDSEarlyReleaseTranche.TRANCHE_1
      isApplicableForTranche2(calculationResult, booking) -> SDSEarlyReleaseTranche.TRANCHE_2
      else -> SDSEarlyReleaseTranche.TRANCHE_0
    }
  }

  private fun getSentencesForTrancheRules(booking: Booking): List<AbstractSentence> =
    booking.sentences.filter { sentence ->
      isEligibleForTrancheRules(sentence) &&
        sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
    }

  private fun isEligibleForTrancheRules(sentence: AbstractSentence): Boolean =
    (
      sentence.identificationTrack == SentenceIdentificationTrack.SDS_EARLY_RELEASE ||
        sentence.identificationTrack == SentenceIdentificationTrack.SDS_STANDARD_RELEASE
      ) &&
      !sentence.isRecall()

  private fun isApplicableForTranche1(calculationResult: CalculationResult, booking: Booking): Boolean =
    tranche.isBookingApplicableForTrancheCriteria(
      calculationResult,
      booking.getAllExtractableSentences(),
      TrancheType.TRANCHE_ONE,
    )

  private fun isApplicableForTranche2(calculationResult: CalculationResult, booking: Booking): Boolean =
    tranche.isBookingApplicableForTrancheCriteria(
      calculationResult,
      booking.getAllExtractableSentences(),
      TrancheType.TRANCHE_TWO,
    )
}
