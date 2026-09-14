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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummaryPage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PageInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PageRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SecondCheckDetails
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
  fun getHistoricCalculationsForPrisoner(prisonerId: String): List<HistoricCalculation> = getHistoricCalculations(prisonerId, null).first

  @Transactional(readOnly = true)
  fun getHistoricCalculationSummaryPage(prisonerId: String, pageRequest: PageRequest): HistoricCalculationSummaryPage {
    val (results, pageInfo) = getHistoricCalculations(prisonerId, pageRequest)
    return HistoricCalculationSummaryPage(
      results.map { historicCalculation ->
        HistoricCalculationSummary(
          calculationDate = historicCalculation.calculationDate,
          calculationSource = historicCalculation.calculationSource,
          calculationType = historicCalculation.calculationType,
          crdsCalculationId = historicCalculation.calculationRequestId,
          nomisCalculationId = historicCalculation.offenderSentCalculationId,
          reasonDescription = historicCalculation.calculationReason ?: "Not entered",
          reasonFurtherDetail = historicCalculation.reasonFurtherDetail,
          genuineOverrideReasonDescription = historicCalculation.genuineOverrideReasonDescription,
          calculatedByDisplayName = historicCalculation.calculatedByDisplayName,
          establishmentCalculatedAtDescription = historicCalculation.establishment,
        )
      },
      pageInfo,
    )
  }

  private fun getHistoricCalculations(prisonerId: String, pageRequest: PageRequest?): Pair<List<HistoricCalculation>, PageInfo> {
    val calculations = calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)
    val secondChecks: List<CalculationRequestSecondCheck> = secondCheckRepository.findAllByPrisonerId(prisonerId)
    val (nomisCalculations, pageInfo) = prisonService.getCalculationsForAPrisonerId(prisonerId).let { calcs ->
      if (pageRequest != null && calcs.isNotEmpty()) {
        val pages = calcs.sortedBy { it.calculationDate }.chunked(pageRequest.size)
        val zeroIndexedPageNumber = maxOf(0, pageRequest.pageNumber - 1)
        val atLeastTheFirstAndNoMoreThanTheLastPage = minOf(pages.size - 1, zeroIndexedPageNumber)
        pages[atLeastTheFirstAndNoMoreThanTheLastPage] to PageInfo(atLeastTheFirstAndNoMoreThanTheLastPage + 1, pages.size, calcs.size)
      } else {
        calcs to PageInfo(1, 1, calcs.size)
      }
    }
    val agencyIdToDescriptionMap = prisonService.getAgenciesByType("INST").associateBy { it.agencyId }
    val uniqueUsers: Set<String> = (
      nomisCalculations.map { it.calculatedByUserId.uppercase() } +
        secondChecks.map { it.checkedByUsername.uppercase() }
      ).toSet()
    val userDetails = manageUsersApiClient.getUsersByUsernames(uniqueUsers)
    val historicCalculations = nomisCalculations.map { nomisCalculation ->
      var source = CalculationSource.NOMIS
      var calculationViewData: CalculationViewConfiguration? = null
      var calculationType: CalculationType? = null
      var calculationRequestId: Long? = null
      var calculationReason: String? = nomisCalculation.calculationReason
      var reasonFurtherDetail: String? = null
      var establishment: String? = null
      val nomisComment = nomisCalculation.commentText
      var genuineOverrideReason: GenuineOverrideReason? = null
      var genuineOverrideReasonDescription: String? = null
      val calculatedByUsername = nomisCalculation.calculatedByUserId
      val userDetail = userDetails?.get(nomisCalculation.calculatedByUserId.uppercase())
      val calculatedByDisplayName = listOfNotNull(userDetail?.firstName, userDetail?.lastName).joinToString(" ")
      var secondCheckDetailsList: List<SecondCheckDetails> = emptyList()
      calculations.firstOrNull {
        nomisComment != null && nomisCalculation.commentText.contains(it.calculationReference.toString())
      }?.let { calc ->
        val filteredSecondChecks = secondChecks
          .filter { secondCheck -> secondCheck.calculationRequestId == calc.id }
          .sortedByDescending { secondCheck -> secondCheck.checkedAt }

        secondCheckDetailsList = filteredSecondChecks.map {
          val checkedByUserDetail = it.checkedByUsername.uppercase().let { username ->
            userDetails?.get(username)
          }
          SecondCheckDetails(
            checkedByUsername = it.checkedByUsername,
            checkedByDisplayName = listOfNotNull(checkedByUserDetail?.firstName, checkedByUserDetail?.lastName)
              .joinToString(" ").ifBlank { it.checkedByUsername },
            checkedAt = it.checkedAt,
          )
        }
        establishment = agencyIdToDescriptionMap[calc.prisonerLocation]?.description
        source = CalculationSource.CRDS
        calculationType = calc.calculationType
        calculationViewData = CalculationViewConfiguration(calc.calculationReference.toString(), calc.id())
        calculationRequestId = calc.id
        calculationReason = calc.reasonForCalculation?.displayName
        reasonFurtherDetail = calc.otherReasonForCalculation
        genuineOverrideReason = calc.genuineOverrideReason
        genuineOverrideReasonDescription = calc.genuineOverrideReasonFurtherDetail ?: calc.genuineOverrideReason?.description
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
        reasonFurtherDetail = reasonFurtherDetail,
        offenderSentCalculationId = nomisCalculation.offenderSentCalculationId,
        genuineOverrideReasonCode = genuineOverrideReason,
        genuineOverrideReasonDescription = genuineOverrideReasonDescription,
        calculatedByUsername = calculatedByUsername,
        calculatedByDisplayName = calculatedByDisplayName,
        secondCheckDetails = secondCheckDetailsList,
      )
    }
    return historicCalculations to pageInfo
  }
}
