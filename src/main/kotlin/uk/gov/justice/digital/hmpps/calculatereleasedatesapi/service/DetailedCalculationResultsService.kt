package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AllocatedTranche
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
  private val sdsLegislationConfiguration: SDSLegislationConfiguration,
) {

  @Transactional(readOnly = true)
  fun findDetailedCalculationResults(calculationRequestId: Long): DetailedCalculationResults {
    val secondCheckDetails = calculationResultEnrichmentService.getSecondCheckDetails(calculationRequestId)
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
      secondCheckDetails = secondCheckDetails,
      approvedDates = approvedDates(calculationRequest.approvedDatesSubmissions.firstOrNull()),
      calculationOriginalData = CalculationOriginalData(
        prisonerDetails,
        sentenceAndOffences,
      ),
      calculationBreakdown = calculationBreakdown,
      breakdownMissingReason = breakdownMissingReason,
      sds40Tranche = calculationRequest.allocatedSDSTranche?.tranche,
      ftr56Tranche = calculationRequest.allocatedSDSTranche?.ftr56Tranche,
      progressionModelTranche = calculationRequest.allocatedSDSTranche?.progressionModelTranche,
      allocatedTranches = resolveTranches(calculationRequest),
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

  private fun resolveTranches(calculationRequest: CalculationRequest): List<AllocatedTranche> {
    val allocated = calculationRequest.allocatedSDSTranche ?: return emptyList()
    val tranches = mutableListOf<AllocatedTranche>()

    if (allocated.ftr56Tranche != null && allocated.ftr56Tranche !== TrancheName.FTR_56_TRANCHE_0) {
      tranches.add(
        AllocatedTranche(
          legislationName = LegislationName.FTR_56,
          trancheName = allocated.ftr56Tranche!!,
          trancheDate = sdsLegislationConfiguration.sds40Legislation.tranches
            .firstOrNull { it.name == allocated.ftr56Tranche }
            ?.date,
        ),
      )
    }

    if (allocated.progressionModelTranche != null && allocated.progressionModelTranche !== TrancheName.TRANCHE_0) {
      tranches.add(
        AllocatedTranche(
          legislationName = LegislationName.SDS_PROGRESSION_MODEL,
          trancheName = allocated.progressionModelTranche!!,
          trancheDate = sdsLegislationConfiguration.progressionModelLegislation
            ?.tranches
            ?.firstOrNull { it.name == allocated.progressionModelTranche }
            ?.date,
        ),
      )
    }

    if (allocated.tranche !== TrancheName.TRANCHE_0 && allocated.affectedBySds40) {
      tranches.add(
        AllocatedTranche(
          legislationName = LegislationName.SDS_40,
          trancheName = allocated.tranche,
          trancheDate = sdsLegislationConfiguration.sds40Legislation.tranches
            .firstOrNull { it.name == allocated.tranche }
            ?.date,
        ),
      )
    }

    return tranches
  }
}
