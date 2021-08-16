package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceTerm
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonerService(@Qualifier("prisonApiWebClient") private val webClient: WebClient) {
  private inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getOffenderDetail(prisonerId: String): PrisonerDetails {
    log.info("Requesting details for prisoner $prisonerId") // TODO remove this logging - only used for test purposes
    return webClient.get()
      .uri("/api/offenders/$prisonerId")
      .retrieve()
      .bodyToMono(typeReference<PrisonerDetails>())
      .block()!!
  }

  fun getBookingDetail(bookingId: Int): PrisonerDetails {
    log.info("Requesting details for bookingId $bookingId")
    return webClient.get()
      .uri("/api/bookings/$bookingId")
      .retrieve()
      .bodyToMono(typeReference<PrisonerDetails>())
      .block()!!
  }

  fun getSentenceAdjustments(bookingId: Int): SentenceAdjustments {
    log.info("Requesting sentence adjustment details for bookingId $bookingId")
    return webClient.get()
      .uri("/api/bookings/$bookingId/sentenceAdjustments")
      .retrieve()
      .bodyToMono(typeReference<SentenceAdjustments>())
      .block()!!
  }

  fun getSentenceTerm(bookingId: Int): List<SentenceTerm> {
    log.info("Requesting sentence adjustment details for bookingId $bookingId")
    return webClient.get()
      .uri("/api/bookings/$bookingId/sentenceTerms")
      .retrieve()
      .bodyToMono(typeReference<List<SentenceTerm>>())
      .block()!!
  }

  fun getBooking(prisonerId: String): Booking {
    val prisonerDetails = getOffenderDetail(prisonerId)
    val sentenceTerms = getSentenceTerm(prisonerDetails.bookingId)
    val sentenceAdjustments = getSentenceAdjustments(prisonerDetails.bookingId)
    val offender = Offender(
      name = prisonerDetails.firstName + ' ' + prisonerDetails.lastName,
      dateOfBirth = prisonerDetails.dateOfBirth,
      reference = prisonerDetails.offenderNo,
    )
    val sentences = sentenceTerms.map {
      val offence = Offence(startedAt = it.startDate)
      val duration = Duration()
//      duration.append(it.days, DAYS)
//      duration.append(it.days, DAYS)
      val sentence = Sentence(sentencedAt = it.sentenceStartDate)

      }

    val booking = Booking(offender = offender,
    sentences = mutableListOf())
    return booking
  }
}
