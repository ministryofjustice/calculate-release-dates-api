package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CaseLoad
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RestResponsePage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.CalculableSentenceEnvelope
import java.time.Duration

@Service
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
  @Qualifier("prisonApiAsyncWebClient") private val asyncWebClient: WebClient,
) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    log.info("Requesting details for prisoner $prisonerId")
    return webClient.get()
      .uri("/api/offenders/$prisonerId")
      .retrieve()
      .bodyToMono(typeReference<PrisonerDetails>())
      .block()!!
  }

  fun getSentenceAndBookingAdjustments(bookingId: Long): BookingAndSentenceAdjustments {
    log.info("Requesting sentence and booking adjustment details for bookingId $bookingId")
    return webClient.get()
      .uri("/api/adjustments/$bookingId/sentence-and-booking")
      .retrieve()
      .bodyToMono(typeReference<BookingAndSentenceAdjustments>())
      .block()!!
  }

  fun getSentencesAndOffences(bookingId: Long): List<SentenceAndOffences> {
    log.info("Requesting sentence terms for bookingId $bookingId")
    return webClient.get()
      .uri("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
      .retrieve()
      .bodyToMono(typeReference<List<SentenceAndOffences>>())
      .block()!!
  }

  fun getFixedTermRecallDetails(bookingId: Long): FixedTermRecallDetails {
    log.info("Requesting return to fixed term recall details for bookingId $bookingId")
    return webClient.get()
      .uri("/api/bookings/$bookingId/fixed-term-recall")
      .retrieve()
      .bodyToMono(typeReference<FixedTermRecallDetails>())
      .block()!!
  }

  fun postReleaseDates(bookingId: Long, updateOffenderDates: UpdateOffenderDates) {
    log.info("Writing release dates to NOMIS for bookingId $bookingId")
    webClient.post()
      .uri("/api/offender-dates/$bookingId")
      .bodyValue(updateOffenderDates)
      .retrieve()
      .toBodilessEntity()
      .block()

    log.info("Writing release dates to NOMIS finished")
  }

  fun getOffenderFinePayments(bookingId: Long): List<OffenderFinePayment> {
    log.info("Requesting offender fine payments for bookingId $bookingId")
    return webClient.get()
      .uri("api/offender-fine-payment/booking/$bookingId")
      .retrieve()
      .bodyToMono(typeReference<List<OffenderFinePayment>>())
      .block()!!
  }

  fun getCurrentUserCaseLoads(): ArrayList<CaseLoad>? {
    return webClient.get()
      .uri("/api/users/me/caseLoads")
      .retrieve()
      .bodyToMono(typeReference<ArrayList<CaseLoad>>())
      .block()
  }

  fun getCalculableSentenceEnvelopesByEstablishment(
    establishmentId: String,
    pageNumber: Int,
    token: String,
  ): RestResponsePage<CalculableSentenceEnvelope> {
    log.info("Requesting personId and booking details for latest booking of all offenders at establishment $establishmentId and page $pageNumber")
    return asyncWebClient.get()
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

  fun getCalculableSentenceEnvelopesByPrisonerIds(prisonerIds: List<String>, token: String): List<CalculableSentenceEnvelope> {
    return asyncWebClient.get()
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
  }

  fun getCalculationsForAPrisonerId(prisonerId: String): List<SentenceCalculationSummary> {
    return webClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/api/offender-dates/calculations/$prisonerId")
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<List<SentenceCalculationSummary>>())
      .block()!!
  }

  fun getAgenciesByType(agencyType: String): List<Agency> {
    log.info("Requesting agencies with type: $agencyType")
    return webClient.get()
      .uri("/api/agencies/type/$agencyType")
      .retrieve()
      .bodyToMono(typeReference<List<Agency>>())
      .block()!!
  }
}
