package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOriginalData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationReasonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
@Transactional(readOnly = true)
@Suppress("RedundantModalityModifier") // required for spring @Transactional
open class DetailedCalculationResultsService(
  private val calculationBreakdownService: CalculationBreakdownService,
  private val sourceDataMapper: SourceDataMapper,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val featureToggles: FeatureToggles,
  private val manageUsersApiClient: ManageUsersApiClient,
  private val prisonService: PrisonService,
) {

  @Transactional(readOnly = true)
  fun findDetailedCalculationResults(calculationRequestId: Long): DetailedCalculationResults {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
    val prisonerDetails = calculationRequest.prisonerDetails?.let { sourceDataMapper.mapPrisonerDetails(calculationRequest) }
    val (breakdownMissingReason, calculationBreakdown) = calculationBreakdownService.getBreakdownSafely(calculationRequest).fold(
      { it to null },
      { null to it },
    )
    val releaseDates = calculationRequest.calculationOutcomes
      .filter { it.outcomeDate != null }
      .map { ReleaseDate(it.outcomeDate!!, ReleaseDateType.valueOf(it.calculationDateType)) }

    val historicSledOverride = calculationOutcomeHistoricOverrideRepository.findByCalculationRequestId(calculationRequestId)
    return DetailedCalculationResults(
      context = calculationContext(calculationRequestId, calculationRequest),
      dates = calculationResultEnrichmentService.addDetailToCalculationDates(
        releaseDates,
        sentenceAndOffences,
        calculationBreakdown,
        calculationRequest.historicalTusedSource,
        null,
        historicSledOverride,
      ),
      approvedDates = approvedDates(calculationRequest.approvedDatesSubmissions.firstOrNull()),
      calculationOriginalData = CalculationOriginalData(
        prisonerDetails,
        sentenceAndOffences,
      ),
      calculationBreakdown = calculationBreakdown,
      breakdownMissingReason = breakdownMissingReason,
      sds40Tranche = calculationRequest.allocatedSDSTranche?.tranche,
      ftr56Tranche = calculationRequest.allocatedSDSTranche?.ftr56Tranche,
      usedPreviouslyRecordedSLED = historicSledOverride?.let {
        PreviouslyRecordedSLED(
          previouslyRecordedSLEDDate = it.historicCalculationOutcomeDate,
          calculatedDate = it.calculationOutcomeDate,
          previouslyRecordedSLEDCalculationRequestId = it.historicCalculationRequestId,
        )
      },
    )
  }

  private fun calculationContext(
    calculationRequestId: Long,
    calculationRequest: CalculationRequest,
  ): CalculationContext {
    val calculatedAtPrisonDescription: String? = if (calculationRequest.prisonerLocation != null) {
      prisonService.getAgenciesByType("INST").firstOrNull { it.agencyId == calculationRequest.prisonerLocation }?.description ?: calculationRequest.prisonerLocation
    } else {
      null
    }
    return CalculationContext(
      calculationRequestId = calculationRequestId,
      bookingId = calculationRequest.bookingId,
      prisonerId = calculationRequest.prisonerId,
      calculationStatus = CalculationStatus.valueOf(calculationRequest.calculationStatus),
      calculationReference = calculationRequest.calculationReference,
      calculationReason = calculationRequest.reasonForCalculation?.let { CalculationReasonDto.from(it) },
      otherReasonDescription = calculationRequest.otherReasonForCalculation,
      calculationDate = calculationRequest.calculatedAt.toLocalDate(),
      calculationType = calculationRequest.calculationType,
      overridesCalculationRequestId = calculationRequest.overridesCalculationRequestId,
      genuineOverrideReasonCode = calculationRequest.genuineOverrideReason,
      genuineOverrideReasonDescription = calculationRequest.genuineOverrideReasonFurtherDetail ?: calculationRequest.genuineOverrideReason?.description,
      usePreviouslyRecordedSLEDIfFound = calculationRequest.calculationRequestUserInput?.usePreviouslyRecordedSLEDIfFound ?: false,
      calculatedByUsername = calculationRequest.calculatedByUsername,
      calculatedByDisplayName = manageUsersApiClient.getUserByUsername(calculationRequest.calculatedByUsername)?.name ?: calculationRequest.calculatedByUsername,
      calculatedAtPrisonId = calculationRequest.prisonerLocation,
      calculatedAtPrisonDescription = calculatedAtPrisonDescription,
    )
  }

  private fun approvedDates(latestApprovedDatesSubmission: ApprovedDatesSubmission?): Map<ReleaseDateType, DetailedDate>? = latestApprovedDatesSubmission?.approvedDates?.associate {
    val type = ReleaseDateType.valueOf(it.calculationDateType)
    type to DetailedDate(type, type.description, it.outcomeDate, emptyList())
  }

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findById(calculationRequestId).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId")
  }
}
