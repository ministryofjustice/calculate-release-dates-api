package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Component
class LatestCalculationService(
  private val prisonService: PrisonService,
  private val offenderKeyDatesService: OffenderKeyDatesService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val featureToggles: FeatureToggles,
  private val manageUsersApiClient: ManageUsersApiClient,
) {

  @Transactional(readOnly = true)
  fun latestCalculationForPrisoner(prisonerId: String): Either<String, LatestCalculation> = getLatestBookingFromPrisoner(prisonerId)
    .flatMap { bookingId -> prisonService.getOffenderKeyDates(bookingId).map { bookingId to it } }
    .map { (bookingId, prisonerCalculation) ->
      val latestCrdsCalc = calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)
      if (latestCrdsCalc.isEmpty || !isSameCalc(prisonerCalculation, latestCrdsCalc.get())) {
        val nomisReason = prisonService.getNOMISCalcReasons().find { it.code == prisonerCalculation.reasonCode }?.description ?: prisonerCalculation.reasonCode
        toLatestNomisCalculation(
          prisonerId,
          bookingId,
          prisonerCalculation,
          nomisReason,
        )
      } else {
        val calculationRequest = latestCrdsCalc.get()
        var location = calculationRequest.prisonerLocation
        if (calculationRequest.prisonerLocation != null) {
          location = prisonService.getAgenciesByType("INST").firstOrNull { it.agencyId == location }?.description ?: location
        }
        val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
        val breakdown = calculationBreakdownService.getBreakdownSafely(calculationRequest).getOrNull()
        toLatestDpsCalculation(
          calculationRequest.id,
          prisonerId,
          bookingId,
          prisonerCalculation,
          calculationRequest.reasonForCalculation?.displayName ?: "Not entered",
          location,
          sentenceAndOffences,
          breakdown,
          calculationRequest.historicalTusedSource,
          calculationRequest.calculatedByUsername,
        )
      }
    }

  private fun isSameCalc(prisonerCalculation: OffenderKeyDates, latestCrdsCalc: CalculationRequest): Boolean = when {
    prisonerCalculation.comment == null -> false
    prisonerCalculation.comment.contains(latestCrdsCalc.calculationReference.toString()) -> true
    else -> false
  }

  private fun getLatestBookingFromPrisoner(prisonerId: String): Either<String, Long> = try {
    prisonService.getOffenderDetail(prisonerId).bookingId.right()
  } catch (e: WebClientResponseException) {
    if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
      "Prisoner ($prisonerId) could not be found".left()
    } else {
      throw e
    }
  }

  private fun toLatestDpsCalculation(
    calculationRequestId: Long,
    prisonerId: String,
    bookingId: Long,
    prisonerCalculation: OffenderKeyDates,
    reason: String,
    location: String?,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    breakdown: CalculationBreakdown?,
    historicalTusedSource: HistoricalTusedSource? = null,
    calculatedByUsername: String,
  ): LatestCalculation {
    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    val historicSledOverride = if (featureToggles.historicSled) calculationOutcomeHistoricOverrideRepository.findByCalculationRequestId(calculationRequestId) else null
    return LatestCalculation(
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculatedAt = prisonerCalculation.calculatedAt,
      calculationRequestId = calculationRequestId,
      establishment = location,
      reason = reason,
      source = CalculationSource.CRDS,
      calculatedByUsername = calculatedByUsername,
      calculatedByDisplayName = manageUsersApiClient.getUserByUsername(calculatedByUsername)?.name ?: calculatedByUsername,
      dates = calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        sentenceAndOffences,
        breakdown,
        historicalTusedSource,
        null,
        historicSledOverride,
      ).values.toList(),
    )
  }

  private fun toLatestNomisCalculation(
    prisonerId: String,
    bookingId: Long,
    prisonerCalculation: OffenderKeyDates,
    reason: String,
  ): LatestCalculation {
    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    return LatestCalculation(
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculatedAt = prisonerCalculation.calculatedAt,
      calculationRequestId = null,
      establishment = null,
      reason = reason,
      source = CalculationSource.NOMIS,
      calculatedByUsername = prisonerCalculation.calculatedByUserId,
      calculatedByDisplayName = prisonerCalculation.calculatedByUserId.let { manageUsersApiClient.getUserByUsername(it)?.name } ?: prisonerCalculation.calculatedByUserId,
      dates = calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        null,
        null,
        null,
        prisonerCalculation,
        null,
      ).values.toList(),
    )
  }
}
