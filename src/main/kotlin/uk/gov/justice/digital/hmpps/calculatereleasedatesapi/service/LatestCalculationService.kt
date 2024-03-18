package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime

@Component
class LatestCalculationService(private val prisonService: PrisonService, private val calculationRequestRepository: CalculationRequestRepository) {

  fun latestCalculationForPrisoner(prisonerId: String): Either<String, LatestCalculation> {
    return getLatestBookingFromPrisoner(prisonerId)
      .flatMap { bookingId -> getOffenderKeyDates(bookingId) }
      .map {prisonerCalculation ->
        val latestCrdsCalc = calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)
        if (latestCrdsCalc.isEmpty || !isSameCalc(prisonerCalculation, latestCrdsCalc.get())) {
          toLatestCalculation(CalculationSource.NOMIS, prisonerId, prisonerCalculation, prisonerCalculation.reasonCode, null, null)
        } else {
          var location = latestCrdsCalc.get().prisonerLocation
          if(latestCrdsCalc.get().prisonerLocation != null) {
              location = prisonService.getAgenciesByType("INST").firstOrNull { it.agencyId == location }?.description ?: location
          }
          toLatestCalculation(CalculationSource.CRDS, prisonerId, prisonerCalculation, latestCrdsCalc.get().reasonForCalculation?.displayName, latestCrdsCalc.get().calculatedAt, location)
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

  private fun getLatestBookingFromPrisoner(prisonerId: String) : Either<String, Long> {
    return try {
      prisonService.getOffenderDetail(prisonerId).bookingId.right()
    }  catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        "Prisoner (${prisonerId}) could not be found".left()
      } else {
        throw e
      }
    }
  }

  private fun getOffenderKeyDates(bookingId: Long): Either<String, OffenderKeyDates> = try {
     prisonService.getOffenderKeyDates(bookingId).right()
  } catch (e: WebClientResponseException) {
    if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
      "Key dates for booking (${bookingId}) could not be found".left()
    } else {
      throw e
    }
  }

  private fun toLatestCalculation(
    calculationSource: CalculationSource,
    prisonerId: String,
    prisonerCalculation: OffenderKeyDates,
    reason: String?,
    calculatedAt: LocalDateTime?,
    location: String?,
  ) = LatestCalculation(
    prisonerId,
    calculatedAt,
    location,
    reason,
    calculationSource,
    listOfNotNull(
      prisonerCalculation.sentenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.SED) },
      prisonerCalculation.licenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.LED) },
      prisonerCalculation.paroleEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.PED) },
      prisonerCalculation.homeDetentionCurfewEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.HDCED) },
      prisonerCalculation.homeDetentionCurfewApprovedDate?.let { ReleaseDate(it, ReleaseDateType.HDCAD) },
      prisonerCalculation.automaticReleaseDate?.let { ReleaseDate(it, ReleaseDateType.ARD) },
      prisonerCalculation.conditionalReleaseDate?.let { ReleaseDate(it, ReleaseDateType.CRD) },
      prisonerCalculation.nonParoleDate?.let { ReleaseDate(it, ReleaseDateType.NPD) },
      prisonerCalculation.postRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.PRRD) },
      prisonerCalculation.approvedParoleDate?.let { ReleaseDate(it, ReleaseDateType.APD) },
      prisonerCalculation.topupSupervisionExpiryDate?.let { ReleaseDate(it, ReleaseDateType.TUSED) },
      prisonerCalculation.earlyTermDate?.let { ReleaseDate(it, ReleaseDateType.ETD) },
      prisonerCalculation.midTermDate?.let { ReleaseDate(it, ReleaseDateType.MTD) },
      prisonerCalculation.lateTermDate?.let { ReleaseDate(it, ReleaseDateType.LTD) },
      prisonerCalculation.tariffDate?.let { ReleaseDate(it, ReleaseDateType.Tariff) },
      prisonerCalculation.releaseOnTemporaryLicenceDate?.let { ReleaseDate(it, ReleaseDateType.ROTL) },
      prisonerCalculation.earlyRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.ERSED) },
      prisonerCalculation.tariffExpiredRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.TERSED) },
      prisonerCalculation.dtoPostRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.DPRRD) },
    ),
  )

}