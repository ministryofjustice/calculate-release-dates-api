package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence

@Service
class TrancheAllocationService(
  private val tranche: Tranche,
  private val trancheConfiguration: SDS40TrancheConfiguration,
) {

  fun calculateTranche(sentences: List<CalculableSentence>): SDSEarlyReleaseTranche {
    // Exclude any sentences where sentencing was after T1 commencement - CRS-2126
    // For the purposes of tranching any SDS is considered, Early release track is predicated on NOT having any exclusions
    //
    // Recalls sentenced before T1 commencement CAN NOT be recall for a SDS40 sentence, however a full implementation
    //
    // That looks at movements and date of release to determine release conditions applicable is yet to be implemented.
    val sentencesConsideredForTrancheRules = getSentencesForTrancheRules(sentences)

    return when {
      sentencesConsideredForTrancheRules.isEmpty() -> SDSEarlyReleaseTranche.TRANCHE_0
      isApplicableForTranche1(sentences) -> SDSEarlyReleaseTranche.TRANCHE_1
      isApplicableForTranche2(sentences) -> SDSEarlyReleaseTranche.TRANCHE_2
      else -> SDSEarlyReleaseTranche.TRANCHE_0
    }
  }

  private fun getSentencesForTrancheRules(sentences: List<CalculableSentence>): List<CalculableSentence> = sentences.flatMap { it.sentenceParts() }.filter { sentence ->
    isEligibleForTrancheRules(sentence) &&
      sentence.sentencedAt.isBefore(trancheConfiguration.trancheOneCommencementDate)
  }

  private fun isEligibleForTrancheRules(sentence: CalculableSentence): Boolean = (
    sentence.identificationTrack.isEarlyReleaseTrancheOneTwo() ||
      sentence.identificationTrack == SentenceIdentificationTrack.SDS_STANDARD_RELEASE
    ) &&
    !sentence.isRecall()

  private fun isApplicableForTranche1(sentences: List<CalculableSentence>): Boolean = tranche.isBookingApplicableForTrancheCriteria(
    sentences,
    TrancheType.TRANCHE_ONE,
  )

  private fun isApplicableForTranche2(sentences: List<CalculableSentence>): Boolean = tranche.isBookingApplicableForTrancheCriteria(
    sentences,
    TrancheType.TRANCHE_TWO,
  )
}
