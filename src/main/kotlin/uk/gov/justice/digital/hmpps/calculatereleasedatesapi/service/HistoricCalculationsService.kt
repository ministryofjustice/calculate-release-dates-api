package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSecondCheck
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.SecondCheckRepository

@Service
class HistoricCalculationsService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val manageUsersApiClient: ManageUsersApiClient,
  private val secondCheckRepository: SecondCheckRepository,
) {

  @Transactional(readOnly = true)
  fun getHistoricCalculationsForPrisoner(prisonerId: String): List<HistoricCalculation> {
    val calculations = calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)
    val secondChecks: List<CalculationRequestSecondCheck> = secondCheckRepository.findAllByPrisonerId(prisonerId)
    val nomisCalculations = prisonService.getCalculationsForAPrisonerId(prisonerId)
    val agencyIdToDescriptionMap = prisonService.getAgenciesByType("INST").associateBy { it.agencyId }
    val uniqueUsers: Set<String> = nomisCalculations.mapNotNull { it.calculatedByUserId?.uppercase() }.toSet()
    val userDetails = manageUsersApiClient.getUsersByUsernames(uniqueUsers)
    val secondCheckCalculations = secondChecks.map { secondCheck: CalculationRequestSecondCheck ->
      val userDetail = userDetails?.get(secondCheck.checkedByUsername.uppercase())
      val calculatedByDisplayName = listOfNotNull(userDetail?.firstName, userDetail?.lastName).joinToString(" ")
      HistoricCalculation(
        offenderNo = prisonerId,
        calculationDate = secondCheck.checkedAt,
        calculationSource = CalculationSource.CRDS,
        calculationViewConfiguration = null,
        commentText = CalculationType.SECOND_CHECK.name,
        calculationType = CalculationType.SECOND_CHECK,
        establishment = agencyIdToDescriptionMap[secondCheck.calculationRequest.prisonerLocation]?.description,
        calculationRequestId = secondCheck.calculationRequest.id(),
        calculationReason = CalculationType.SECOND_CHECK.name,
        offenderSentCalculationId = 0L,
        calculatedByUsername = secondCheck.checkedByUsername.uppercase(),
        genuineOverrideReasonCode = null,
        genuineOverrideReasonDescription = null,
        calculatedByDisplayName = calculatedByDisplayName,
      )
    }
    val historicCalculations = nomisCalculations.map { nomisCalculation ->
      var source = CalculationSource.NOMIS
      var calculationViewData: CalculationViewConfiguration? = null
      var calculationType: CalculationType? = null
      var calculationRequestId: Long? = null
      var calculationReason: String? = nomisCalculation.calculationReason
      var establishment: String? = null
      val nomisComment = nomisCalculation.commentText
      var genuineOverrideReason: GenuineOverrideReason? = null
      var genuineOverrideReasonDescription: String? = null
      val calculatedByUsername = nomisCalculation.calculatedByUserId
      val userDetail = userDetails?.get(nomisCalculation.calculatedByUserId?.uppercase())
      val calculatedByDisplayName = listOfNotNull(userDetail?.firstName, userDetail?.lastName).joinToString(" ")
      calculations.firstOrNull {
        nomisComment != null && nomisCalculation.commentText.contains(it.calculationReference.toString())
      }?.let {
        establishment = agencyIdToDescriptionMap[it.prisonerLocation]?.description
        source = CalculationSource.CRDS
        calculationType = it.calculationType
        calculationViewData = CalculationViewConfiguration(it.calculationReference.toString(), it.id())
        calculationRequestId = it.id
        calculationReason = it.reasonForCalculation?.displayName
        genuineOverrideReason = it.genuineOverrideReason
        genuineOverrideReasonDescription = it.genuineOverrideReasonFurtherDetail ?: it.genuineOverrideReason?.description
      }

      HistoricCalculation(
        offenderNo = prisonerId,
        calculationDate = nomisCalculation.calculationDate,
        calculationSource = source,
        calculationViewConfiguration = calculationViewData,
        commentText = nomisCalculation.commentText,
        calculationType = calculationType,
        establishment = establishment,
        calculationRequestId = calculationRequestId,
        calculationReason = calculationReason,
        offenderSentCalculationId = nomisCalculation.offenderSentCalculationId,
        genuineOverrideReasonCode = genuineOverrideReason,
        genuineOverrideReasonDescription = genuineOverrideReasonDescription,
        calculatedByUsername = calculatedByUsername,
        calculatedByDisplayName = calculatedByDisplayName,
      )
    }
    val historicDPSAndNomisCalculation: List<HistoricCalculation> = (historicCalculations + secondCheckCalculations).sortedByDescending { it.calculationDate }
    return historicDPSAndNomisCalculation
  }
}
