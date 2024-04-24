package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences

@Service
class PrisonApiDataMapper(private val objectMapper: ObjectMapper) {

  fun mapSentencesAndOffences(calculationRequest: CalculationRequest): List<SentenceAndOffencesWithReleaseArrangements> {
    return when (calculationRequest.sentenceAndOffencesVersion) {
      0 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiDataVersions.Version0.SentenceAndOffences>>() {})
        val sentencesAndOffences: List<PrisonApiDataVersions.Version0.SentenceAndOffences> = reader.readValue(calculationRequest.sentenceAndOffences)
        sentencesAndOffences.map { it.toLatest() }
      }
      1 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiSentenceAndOffences>>() {})
        reader.readValue<List<PrisonApiSentenceAndOffences>>(calculationRequest.sentenceAndOffences).map { SentenceAndOffencesWithReleaseArrangements(it, false) }
      }
      else -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<SentenceAndOffencesWithReleaseArrangements>>() {})
        reader.readValue(calculationRequest.sentenceAndOffences)
      }
    }
  }

  fun mapPrisonerDetails(calculationRequest: CalculationRequest): PrisonerDetails {
    return objectMapper.convertValue(calculationRequest.prisonerDetails, PrisonerDetails::class.java)
  }

  fun mapBookingAndSentenceAdjustments(calculationRequest: CalculationRequest): BookingAndSentenceAdjustments {
    return objectMapper.convertValue(calculationRequest.adjustments, BookingAndSentenceAdjustments::class.java)
  }

  fun mapReturnToCustodyDate(calculationRequest: CalculationRequest): ReturnToCustodyDate {
    return objectMapper.convertValue(calculationRequest.returnToCustodyDate, ReturnToCustodyDate::class.java)
  }
  fun mapOffenderFinePayment(calculationRequest: CalculationRequest): List<OffenderFinePayment> {
    val reader = objectMapper.readerFor(object : TypeReference<List<OffenderFinePayment>>() {})
    return reader.readValue(calculationRequest.offenderFinePayments)
  }
}
