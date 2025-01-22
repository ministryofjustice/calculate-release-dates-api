package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CaseLoad
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RestResponsePage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceDetail
import java.time.Duration
import java.time.LocalDate

@Service
class PrisonApiClient(
  @Qualifier("prisonApiUserAuthWebClient") private val userAuthWebClient: WebClient,
  @Qualifier("prisonApiSystemAuthWebClient") private val systemAuthWebClient: WebClient,
) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    log.info("Requesting details for prisoner $prisonerId")
    return userAuthWebClient.get()
      .uri("/api/offenders/$prisonerId")
      .retrieve()
      .bodyToMono(typeReference<PrisonerDetails>())
      .block()!!
  }

  fun getSentenceDetail(bookingId: Long): SentenceDetail = userAuthWebClient.get()
    .uri("/api/bookings/$bookingId/sentenceDetail")
    .retrieve()
    .bodyToMono(typeReference<SentenceDetail>())
    .block()!!

  fun getSentenceAndBookingAdjustments(bookingId: Long): BookingAndSentenceAdjustments {
    log.info("Requesting sentence and booking adjustment details for bookingId $bookingId")
    return userAuthWebClient.get()
      .uri("/api/adjustments/$bookingId/sentence-and-booking")
      .retrieve()
      .bodyToMono(typeReference<BookingAndSentenceAdjustments>())
      .block()!!
  }

  fun getSentencesAndOffences(bookingId: Long): List<PrisonApiSentenceAndOffences> {
    log.info("Requesting sentence terms for bookingId $bookingId")
    return userAuthWebClient.get()
      .uri("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
      .retrieve()
      .bodyToMono(typeReference<List<PrisonApiSentenceAndOffences>>())
      .block()!!
  }

  fun getFixedTermRecallDetails(bookingId: Long): FixedTermRecallDetails {
    log.info("Requesting return to fixed term recall details for bookingId $bookingId")
    return userAuthWebClient.get()
      .uri("/api/bookings/$bookingId/fixed-term-recall")
      .retrieve()
      .bodyToMono(typeReference<FixedTermRecallDetails>())
      .block()!!
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) {
    log.info("Writing release dates to NOMIS for bookingId $bookingId")
    userAuthWebClient.post()
      .uri("/api/offender-dates/$bookingId")
      .bodyValue(updateOffenderDates)
      .retrieve()
      .toBodilessEntity()
      .block()

    log.info("Writing release dates to NOMIS finished")
  }

  fun getOffenderFinePayments(bookingId: Long): List<OffenderFinePayment> {
    log.info("Requesting offender fine payments for bookingId $bookingId")
    return userAuthWebClient.get()
      .uri("api/offender-fine-payment/booking/$bookingId")
      .retrieve()
      .bodyToMono(typeReference<List<OffenderFinePayment>>())
      .block()!!
  }

  fun getCurrentUserCaseLoads(): ArrayList<CaseLoad>? = userAuthWebClient.get()
    .uri("/api/users/me/caseLoads")
    .retrieve()
    .bodyToMono(typeReference<ArrayList<CaseLoad>>())
    .block()

  fun getCalculableSentenceEnvelopesByEstablishment(
    establishmentId: String,
    pageNumber: Int,
    token: String,
  ): RestResponsePage<CalculableSentenceEnvelope> {
    log.info("Requesting personId and booking details for latest booking of all offenders at establishment $establishmentId and page $pageNumber")
    return systemAuthWebClient.get()
      .uri("/api/prison/$establishmentId/booking/latest/paged/calculable-sentence-envelope?page=$pageNumber")
      .header("Authorization", token)
      .httpRequest { httpRequest ->
        run {
          val reactorRequest = httpRequest.getNativeRequest<HttpClientRequest>()
          reactorRequest.responseTimeout(Duration.ofMinutes(10))
        }
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CalculableSentenceEnvelope>>())
      .block()!!
  }

  fun getCalculableSentenceEnvelopesByPrisonerIds(prisonerIds: List<String>, token: String): List<CalculableSentenceEnvelope> = systemAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/bookings/latest/calculable-sentence-envelope")
        .queryParam("offenderNo", prisonerIds)
        .build()
    }
    .header("Authorization", token)
    .httpRequest { httpRequest ->
      run {
        val reactorRequest = httpRequest.getNativeRequest<HttpClientRequest>()
        reactorRequest.responseTimeout(Duration.ofMinutes(10))
      }
    }
    .retrieve()
    .bodyToMono(typeReference<List<CalculableSentenceEnvelope>>())
    .block()!!

  fun getCalculationsForAPrisonerId(prisonerId: String): List<SentenceCalculationSummary> = userAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/offender-dates/calculations/$prisonerId")
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<List<SentenceCalculationSummary>>())
    .block()!!

  fun getOffenderKeyDates(bookingId: Long): Either<String, OffenderKeyDates> = userAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/offender-dates/$bookingId")
        .build()
    }
    .exchangeToMono { response ->
      when (response.statusCode()) {
        HttpStatus.OK -> response.bodyToMono(typeReference<OffenderKeyDates>()).map { it.right() }
        HttpStatus.NOT_FOUND -> Mono.just("Booking ($bookingId) not found or has no calculations".left())
        HttpStatus.FORBIDDEN -> Mono.just("User is not allowed to view the booking ($bookingId)".left())
        else -> Mono.just("Booking ($bookingId) could not be loaded for an unknown reason. Status ${response.statusCode().value()}".left())
      }
    }
    .block()!!

  fun getAgenciesByType(agencyType: String): List<Agency> {
    log.info("Requesting agencies with type: $agencyType")
    return userAuthWebClient.get()
      .uri("/api/agencies/type/$agencyType")
      .retrieve()
      .bodyToMono(typeReference<List<Agency>>())
      .block()!!
  }

  fun getNOMISCalcReasons(): List<NomisCalculationReason> = userAuthWebClient.get()
    .uri("/api/reference-domains/domains/CALC_REASON/codes")
    .retrieve()
    .bodyToMono(typeReference<List<NomisCalculationReason>>())
    .block()!!

  fun getNOMISOffenderKeyDates(offenderSentCalcId: Long): Either<String, OffenderKeyDates> = userAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/offender-dates/sentence-calculation/$offenderSentCalcId")
        .build()
    }
    .exchangeToMono { response ->
      when (response.statusCode()) {
        HttpStatus.OK -> response.bodyToMono(typeReference<OffenderKeyDates>()).map { it.right() }
        HttpStatus.NOT_FOUND -> Mono.just("Offender Key Dates for offenderSentCalcId ($offenderSentCalcId) not found or has no calculations".left())
        HttpStatus.FORBIDDEN -> Mono.just("User is not allowed to view the Offender Key Dates for offenderSentCalcId ($offenderSentCalcId)".left())
        else -> Mono.just("Offender Key Dates for offenderSentCalcId ($offenderSentCalcId) could not be loaded for an unknown reason. Status ${response.statusCode().value()}".left())
      }
    }
    .block()!!

  fun getExternalMovements(prisonerId: String, earliestSentenceDate: LocalDate): List<PrisonApiExternalMovement> {
    log.info("Requesting external movements for $prisonerId")
    return systemAuthWebClient.get()
      .uri("/api/movements/offender/$prisonerId?allBookings=true&movementTypes=ADM&movementTypes=REL&movementsAfter=$earliestSentenceDate")
      .retrieve()
      .bodyToMono(typeReference<List<PrisonApiExternalMovement>>())
      .block()!!
  }

  fun getLatestTusedDataForBotus(nomisId: String): Either<String, NomisTusedData> = userAuthWebClient.get()
    .uri("/api/offender-dates/latest-tused/$nomisId")
    .exchangeToMono { response ->
      when (response.statusCode()) {
        HttpStatus.OK -> response.bodyToMono(typeReference<NomisTusedData>()).map { it.right() }
        HttpStatus.BAD_REQUEST -> Mono.just("Bad request".left())
        HttpStatus.FORBIDDEN -> Mono.just("User not authorised to access Tused".left())
        HttpStatus.NOT_FOUND -> Mono.just("No Tused could be retrieved for Nomis ID $nomisId".left())
        else -> Mono.just("Unknown: status code was ${response.statusCode().value()}".left())
      }
    }.block()!!
}
