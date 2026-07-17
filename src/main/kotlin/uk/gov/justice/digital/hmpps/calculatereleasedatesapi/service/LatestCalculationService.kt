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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.SecondCheckRepository

@Component
class LatestCalculationService(
  private val prisonService: PrisonService,
  private val offenderKeyDatesService: OffenderKeyDatesService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val manageUsersApiClient: ManageUsersApiClient,
  private val secondCheckRepository: SecondCheckRepository,
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
          calculationRequest.id(),
          prisonerId,
          bookingId,
          prisonerCalculation,
          calculationRequest.reasonForCalculation?.displayName ?: "Not entered",
          calculationRequest.otherReasonForCalculation,
          location,
          sentenceAndOffences,
          breakdown,
          calculationRequest.historicalTusedSource,
          calculationRequest.calculatedByUsername,
          calculationRequest.calculationType.name,
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
    reasonFurtherDetail: String?,
    location: String?,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    breakdown: CalculationBreakdown?,
    historicalTusedSource: HistoricalTusedSource? = null,
    calculatedByUsername: String,
    calculationType: String,
  ): LatestCalculation {
    val secondCheck = secondCheckRepository.findLatestByCalculationRequestId(calculationRequestId)
    val uniqueUsers: Set<String> = listOfNotNull(
      secondCheck?.checkedByUsername?.uppercase(),
      calculatedByUsername.uppercase(),
    ).toSet()
    val userDetails = manageUsersApiClient.getUsersByUsernames(uniqueUsers)
    val checkedByUserDetail = secondCheck?.checkedByUsername?.uppercase()?.let { username ->
      userDetails?.get(username)
    }
    val calculatedByUserDetail = calculatedByUsername.uppercase().let { username ->
      userDetails?.get(username)
    }
    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    val historicSledOverride = calculationOutcomeHistoricOverrideRepository.findByCalculationRequestId(calculationRequestId)
    return LatestCalculation(
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculatedAt = prisonerCalculation.calculatedAt,
      calculationRequestId = calculationRequestId,
      establishment = location,
      reason = reason,
      reasonFurtherDetail = reasonFurtherDetail,
      source = CalculationSource.CRDS,
      calculatedByUsername = calculatedByUsername,
      checkedByUsername = secondCheck?.checkedByUsername,
      checkedAt = secondCheck?.checkedAt,
      calculatedByDisplayName = listOfNotNull(calculatedByUserDetail?.firstName, calculatedByUserDetail?.lastName).joinToString(" ").ifBlank { calculatedByUsername },
      checkedByDisplayName = secondCheck?.checkedByUsername?.let { username -> listOfNotNull(checkedByUserDetail?.firstName, checkedByUserDetail?.lastName).joinToString(" ").ifBlank { username } },
      calculationType = calculationType,
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
      reasonFurtherDetail = null,
      source = CalculationSource.NOMIS,
      calculatedByUsername = prisonerCalculation.calculatedByUserId,
      calculatedByDisplayName = prisonerCalculation.calculatedByUserId.let { manageUsersApiClient.getUserByUsername(it)?.name } ?: prisonerCalculation.calculatedByUserId,
      calculationType = "Unknown",
      checkedAt = null,
      checkedByUsername = null,
      checkedByDisplayName = null,
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
