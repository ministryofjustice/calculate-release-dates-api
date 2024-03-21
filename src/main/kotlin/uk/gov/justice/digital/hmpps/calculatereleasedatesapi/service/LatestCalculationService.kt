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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository

@Component
class LatestCalculationService(
  private val prisonService: PrisonService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val prisonApiDataMapper: PrisonApiDataMapper,
) {

  @Transactional(readOnly = true)
  fun latestCalculationForPrisoner(prisonerId: String): Either<String, LatestCalculation> {
    return getLatestBookingFromPrisoner(prisonerId)
      .flatMap { bookingId -> prisonService.getOffenderKeyDates(bookingId).map { bookingId to it } }
      .map { (bookingId, prisonerCalculation) ->
        val latestCrdsCalc = calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)
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
          val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { prisonApiDataMapper.mapSentencesAndOffences(calculationRequest) }
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
          )
        }
      }
  }

  private fun isSameCalc(prisonerCalculation: OffenderKeyDates, latestCrdsCalc: CalculationRequest): Boolean {
    return when {
      prisonerCalculation.comment == null -> false
      prisonerCalculation.comment.contains(latestCrdsCalc.calculationReference.toString()) -> true
      else -> false
    }
  }

  private fun getLatestBookingFromPrisoner(prisonerId: String): Either<String, Long> {
    return try {
      prisonService.getOffenderDetail(prisonerId).bookingId.right()
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        "Prisoner ($prisonerId) could not be found".left()
      } else {
        throw e
      }
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
    sentenceAndOffences: List<SentenceAndOffences>?,
    breakdown: CalculationBreakdown?,
  ): LatestCalculation {
    val sled = createASLEDIfWeCan(prisonerCalculation)
    val dates = listOfNotNull(
      sled,
      if (sled != null) null else prisonerCalculation.sentenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.SED) },
      if (sled != null) null else prisonerCalculation.licenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.LED) },
      prisonerCalculation.conditionalReleaseDate?.let { ReleaseDate(it, ReleaseDateType.CRD) },
      prisonerCalculation.automaticReleaseDate?.let { ReleaseDate(it, ReleaseDateType.ARD) },
      prisonerCalculation.homeDetentionCurfewEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.HDCED) },
      prisonerCalculation.topupSupervisionExpiryDate?.let { ReleaseDate(it, ReleaseDateType.TUSED) },
      prisonerCalculation.postRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.PRRD) },
      prisonerCalculation.paroleEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.PED) },
      prisonerCalculation.releaseOnTemporaryLicenceDate?.let { ReleaseDate(it, ReleaseDateType.ROTL) },
      prisonerCalculation.earlyRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.ERSED) },
      prisonerCalculation.homeDetentionCurfewApprovedDate?.let { ReleaseDate(it, ReleaseDateType.HDCAD) },
      prisonerCalculation.midTermDate?.let { ReleaseDate(it, ReleaseDateType.MTD) },
      prisonerCalculation.earlyTermDate?.let { ReleaseDate(it, ReleaseDateType.ETD) },
      prisonerCalculation.lateTermDate?.let { ReleaseDate(it, ReleaseDateType.LTD) },
      prisonerCalculation.approvedParoleDate?.let { ReleaseDate(it, ReleaseDateType.APD) },
      prisonerCalculation.nonParoleDate?.let { ReleaseDate(it, ReleaseDateType.NPD) },
      prisonerCalculation.dtoPostRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.DPRRD) },
      prisonerCalculation.tariffDate?.let { ReleaseDate(it, ReleaseDateType.Tariff) },
      prisonerCalculation.tariffExpiredRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.TERSED) },
    )
    return LatestCalculation(
      prisonerId,
      bookingId,
      prisonerCalculation.calculatedAt,
      calculationReference,
      location,
      reason,
      calculationSource,
      calculationResultEnrichmentService.addDetailToCalculationDates(dates, sentenceAndOffences, breakdown).values.toList(),
    )
  }

  private fun createASLEDIfWeCan(prisonerCalculation: OffenderKeyDates): ReleaseDate? {
    return if (prisonerCalculation.sentenceExpiryDate != null && prisonerCalculation.licenceExpiryDate != null && prisonerCalculation.sentenceExpiryDate == prisonerCalculation.licenceExpiryDate) {
      ReleaseDate(prisonerCalculation.sentenceExpiryDate, ReleaseDateType.SLED)
    } else {
      null
    }
  }
}
