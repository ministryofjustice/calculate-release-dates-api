package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOriginalData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Service
@Transactional(readOnly = true)
@Suppress("RedundantModalityModifier") // required for spring @Transactional
open class DetailedCalculationResultsService(
  private val calculationBreakdownService: CalculationBreakdownService,
  private val prisonApiDataMapper: PrisonApiDataMapper,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val prisonService: PrisonService,
) {

  @Transactional(readOnly = true)
  fun findDetailedCalculationResults(calculationRequestId: Long): DetailedCalculationResults {
    val calculationRequest = getCalculationRequest(calculationRequestId)
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { prisonApiDataMapper.mapSentencesAndOffences(calculationRequest) }
    val prisonerDetails = calculationRequest.prisonerDetails?.let { prisonApiDataMapper.mapPrisonerDetails(calculationRequest) }
    val (breakdownMissingReason, calculationBreakdown) = calculationBreakdownService.getBreakdownSafely(calculationRequest).fold(
      { it to null },
      { null to it },
    )
    val releaseDates = calculationRequest.calculationOutcomes
      .filter { it.outcomeDate != null }
      .map { ReleaseDate(it.outcomeDate!!, ReleaseDateType.valueOf(it.calculationDateType)) }

    val sentenceDateOverrides = prisonService.getSentenceOverrides(calculationRequest.bookingId, releaseDates)

    return DetailedCalculationResults(
      calculationContext(calculationRequestId, calculationRequest),
      calculationResultEnrichmentService.addDetailToCalculationDates(
        releaseDates,
        sentenceAndOffences,
        calculationBreakdown,
        calculationRequest.historicalTusedSource,
        sentenceDateOverrides,
      ),
      approvedDates(calculationRequest.approvedDatesSubmissions.firstOrNull()),
      CalculationOriginalData(
        prisonerDetails,
        sentenceAndOffences,
      ),
      calculationBreakdown,
      breakdownMissingReason,
      calculationRequest.allocatedSDSTranche?.tranche,
    )
  }

  private fun calculationContext(
    calculationRequestId: Long,
    calculationRequest: CalculationRequest,
  ) = CalculationContext(
    calculationRequestId,
    calculationRequest.bookingId,
    calculationRequest.prisonerId,
    CalculationStatus.valueOf(calculationRequest.calculationStatus),
    calculationRequest.calculationReference,
    calculationRequest.reasonForCalculation,
    calculationRequest.otherReasonForCalculation,
    calculationRequest.calculatedAt.toLocalDate(),
    calculationRequest.calculationType,
  )

  private fun approvedDates(latestApprovedDatesSubmission: ApprovedDatesSubmission?): Map<ReleaseDateType, DetailedDate>? = latestApprovedDatesSubmission?.approvedDates?.associate {
    val type = ReleaseDateType.valueOf(it.calculationDateType)
    type to DetailedDate(type, type.description, it.outcomeDate, emptyList())
  }

  private fun getCalculationRequest(calculationRequestId: Long): CalculationRequest = calculationRequestRepository.findById(calculationRequestId).orElseThrow {
    EntityNotFoundException("No calculation results exist for calculationRequestId $calculationRequestId")
  }
}
