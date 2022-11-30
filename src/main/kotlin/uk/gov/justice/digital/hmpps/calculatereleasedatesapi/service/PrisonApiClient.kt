package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates

@Service
class PrisonApiClient(@Qualifier("prisonApiWebClient") private val webClient: WebClient) {
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
}
