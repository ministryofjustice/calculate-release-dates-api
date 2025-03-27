package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Component
class LatestCalculationService(
  private val prisonService: PrisonService,
  private val offenderKeyDatesService: OffenderKeyDatesService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val sourceDataMapper: SourceDataMapper,
) {

  @Transactional(readOnly = true)
  fun latestCalculationForPrisoner(prisonerId: String): Either<String, LatestCalculation> = getLatestBookingFromPrisoner(prisonerId)
    .flatMap { bookingId -> prisonService.getOffenderKeyDates(bookingId).map { bookingId to it } }
    .map { (bookingId, prisonerCalculation) ->
      val latestCrdsCalc = calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)
      if (latestCrdsCalc.isEmpty || !isSameCalc(prisonerCalculation, latestCrdsCalc.get())) {
        val nomisReason = prisonService.getNOMISCalcReasons().find { it.code == prisonerCalculation.reasonCode }?.description ?: prisonerCalculation.reasonCode
        toLatestCalculation(
          CalculationSource.NOMIS,
          prisonerId,
          bookingId,
          null,
          prisonerCalculation,
          nomisReason,
          null,
          null,
          null,
        )
      } else {
        val calculationRequest = latestCrdsCalc.get()
        var location = calculationRequest.prisonerLocation
        if (calculationRequest.prisonerLocation != null) {
          location = prisonService.getAgenciesByType("INST").firstOrNull { it.agencyId == location }?.description ?: location
        }
        val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
        val breakdown = calculationBreakdownService.getBreakdownSafely(calculationRequest).getOrNull()
        toLatestCalculation(
          CalculationSource.CRDS,
          prisonerId,
          bookingId,
          calculationRequest.id,
          prisonerCalculation,
          calculationRequest.reasonForCalculation?.displayName ?: "Not entered",
          location,
          sentenceAndOffences,
          breakdown,
          calculationRequest.historicalTusedSource,
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

  private fun toLatestCalculation(
    calculationSource: CalculationSource,
    prisonerId: String,
    bookingId: Long,
    calculationReference: Long?,
    prisonerCalculation: OffenderKeyDates,
    reason: String,
    location: String?,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    breakdown: CalculationBreakdown?,
    historicalTusedSource: HistoricalTusedSource? = null,
  ): LatestCalculation {
    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    val sentenceDateOverrides = prisonService.getSentenceOverrides(bookingId, dates)
    return LatestCalculation(
      prisonerId,
      bookingId,
      prisonerCalculation.calculatedAt,
      calculationReference,
      location,
      reason,
      calculationSource,
      calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        sentenceAndOffences,
        breakdown,
        historicalTusedSource,
        sentenceDateOverrides,
      ).values.toList(),
    )
  }
}
