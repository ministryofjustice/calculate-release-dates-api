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
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitchAgency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceDetail
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.PrisonerInPrisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManageOffencesApiClient.MaxRetryAchievedException
import java.time.Duration
import java.time.LocalDate

@Service
class PrisonApiClient(
  @Qualifier("prisonApiUserAuthWebClient") private val userAuthWebClient: WebClient,
  @Qualifier("prisonApiSystemAuthWebClient") private val systemAuthWebClient: WebClient,
) {
  private inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    log.info("Requesting details for prisoner $prisonerId")
    return systemAuthWebClient.get()
      .uri("/api/offenders/$prisonerId")
      .retrieve()
      .bodyToMono(typeReference<PrisonerDetails>())
      .block()!!
  }

  fun getSentenceDetail(bookingId: Long): SentenceDetail = systemAuthWebClient.get()
    .uri("/api/bookings/$bookingId/sentenceDetail")
    .retrieve()
    .bodyToMono(typeReference<SentenceDetail>())
    .block()!!

  fun getSentenceAndBookingAdjustments(bookingId: Long): BookingAndSentenceAdjustments {
    log.info("Requesting sentence and booking adjustment details for bookingId $bookingId")
    return systemAuthWebClient.get()
      .uri("/api/adjustments/$bookingId/sentence-and-booking")
      .retrieve()
      .bodyToMono(typeReference<BookingAndSentenceAdjustments>())
      .block()!!
  }

  fun getSentencesAndOffences(bookingId: Long): List<PrisonApiSentenceAndOffences> {
    log.info("Requesting sentence terms for bookingId $bookingId")
    return systemAuthWebClient.get()
      .uri("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
      .retrieve()
      .bodyToMono(typeReference<List<PrisonApiSentenceAndOffences>>())
      .block()!!
  }

  fun getFixedTermRecallDetails(bookingId: Long): FixedTermRecallDetails? {
    log.info("Requesting return to fixed term recall details for bookingId $bookingId")
    return systemAuthWebClient.get()
      .uri("/api/bookings/$bookingId/fixed-term-recall")
      .retrieve()
      .bodyToMono(typeReference<FixedTermRecallDetails>())
      .onErrorResume(WebClientResponseException.NotFound::class.java) { _ -> Mono.empty() }
      .block()
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
    return systemAuthWebClient.get()
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

  fun getCalculablePrisonerByPrison(
    establishmentId: String,
    pageNumber: Int,
  ): RestResponsePage<CalculablePrisoner> {
    log.info("Requesting calculable prisoners at establishment $establishmentId and page $pageNumber")
    return systemAuthWebClient.get()
      .uri("/api/prison/$establishmentId/booking/latest/paged/calculable-prisoner?page=$pageNumber")
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CalculablePrisoner>>())
      .retryWhen(
        Retry.backoff(5, Duration.ofSeconds(2))
          .maxBackoff(Duration.ofSeconds(10))
          .doBeforeRetry { retrySignal ->
            log.warn("getCalculablePrisonerByPrison: Retrying [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
          }
          .onRetryExhaustedThrow { _, _ ->
            throw MaxRetryAchievedException("getCalculablePrisonerByPrison: Max retries - lookup failed for $establishmentId")
          },
      )
      .block()!!
  }

  fun getCalculationsForAPrisonerId(prisonerId: String): List<SentenceCalculationSummary> = systemAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/offender-dates/calculations/$prisonerId")
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<List<SentenceCalculationSummary>>())
    .block()!!

  fun getOffenderKeyDates(bookingId: Long): Either<String, OffenderKeyDates> = systemAuthWebClient.get()
    .uri { uriBuilder ->
      uriBuilder.path("/api/offender-dates/$bookingId")
        .build()
    }
    .exchangeToMono { response ->
      when (response.statusCode()) {
        HttpStatus.OK -> response.bodyToMono(typeReference<OffenderKeyDates>()).map { it.right() }
        HttpStatus.NOT_FOUND -> Mono.just("Booking ($bookingId) not found or has no calculations".left())
        HttpStatus.FORBIDDEN -> {
          val errorMsg = "User is not allowed to view the booking ($bookingId)"
          log.error(errorMsg)
          Mono.just(errorMsg.left())
        }

        else -> {
          val errorMsg = "Booking ($bookingId) could not be loaded for an unknown reason. Status ${response.statusCode().value()}"
          log.error(errorMsg)
          Mono.just(errorMsg.left())
        }
      }
    }
    .block()!!

  fun getAgenciesByType(agencyType: String): List<Agency> {
    log.info("Requesting agencies with type: $agencyType")
    return systemAuthWebClient.get()
      .uri("/api/agencies/type/$agencyType")
      .retrieve()
      .bodyToMono(typeReference<List<Agency>>())
      .block()!!
  }

  fun getNOMISCalcReasons(): List<NomisCalculationReason> = systemAuthWebClient.get()
    .uri("/api/reference-domains/domains/CALC_REASON/codes")
    .retrieve()
    .bodyToMono(typeReference<List<NomisCalculationReason>>())
    .block()!!

  fun getNOMISOffenderKeyDates(offenderSentCalcId: Long): Either<String, OffenderKeyDates> = systemAuthWebClient.get()
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

  fun getLatestTusedDataForBotus(nomisId: String): Either<String, NomisTusedData> = systemAuthWebClient.get()
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

  fun getPrisonerInPrisonSummary(prisonerId: String): PrisonerInPrisonSummary {
    log.info("Requesting getPrisonerInPrisonSummary for $prisonerId")
    return systemAuthWebClient.get()
      .uri("/api/offenders/$prisonerId/prison-timeline")
      .retrieve()
      .bodyToMono(PrisonerInPrisonSummary::class.java)
      .block()!!
  }

  fun getAgenciesWithSwitchOn(agencySwitch: AgencySwitch): List<AgencySwitchAgency> {
    log.info("Requesting agencies with switch: $agencySwitch")
    return systemAuthWebClient.get()
      .uri("/api/agency-switches/$agencySwitch")
      .retrieve()
      .bodyToMono(typeReference<List<AgencySwitchAgency>>())
      .block()!!
  }

  fun turnSwitchOnForAgency(agencyId: String, agencySwitch: AgencySwitch): Boolean {
    log.info("Switching on $agencySwitch for $agencyId")
    return systemAuthWebClient.post()
      .uri("/api/agency-switches/$agencySwitch/agency/$agencyId")
      .exchangeToMono { response ->
        when (response.statusCode()) {
          HttpStatus.CREATED -> Mono.just(true)
          HttpStatus.CONFLICT -> Mono.just(true) // it was already switched on
          else -> Mono.just(false)
        }
      }.block()!!
  }

  fun turnSwitchOffForAgency(agencyId: String, agencySwitch: AgencySwitch): Boolean {
    log.info("Switching off $agencySwitch for $agencyId")
    return systemAuthWebClient.delete()
      .uri("/api/agency-switches/$agencySwitch/agency/$agencyId")
      .exchangeToMono { response ->
        when (response.statusCode()) {
          HttpStatus.NO_CONTENT -> Mono.just(true)
          else -> Mono.just(false)
        }
      }.block()!!
  }
}
