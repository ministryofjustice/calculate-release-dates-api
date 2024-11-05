package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithSDSPlus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails

@Service
class PrisonApiDataMapper(private val objectMapper: ObjectMapper) {

  fun mapSentencesAndOffences(calculationRequest: CalculationRequest): List<SentenceAndOffenceWithReleaseArrangements> {
    return when (calculationRequest.sentenceAndOffencesVersion) {
      0 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiDataVersions.Version0.SentenceAndOffences>>() {})
        val sentencesAndOffences: List<PrisonApiDataVersions.Version0.SentenceAndOffences> = reader.readValue(calculationRequest.sentenceAndOffences)
        sentencesAndOffences.flatMap(PrisonApiDataVersions.Version0.SentenceAndOffences::toLatest)
      }
      1 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiSentenceAndOffences>>() {})
        reader.readValue<List<PrisonApiSentenceAndOffences>>(calculationRequest.sentenceAndOffences)
          .flatMap(PrisonApiSentenceAndOffences::toLatest)
      }
      2 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<SentenceAndOffencesWithSDSPlus>>() {})
        reader.readValue<List<SentenceAndOffencesWithSDSPlus>>(calculationRequest.sentenceAndOffences)
          .flatMap(SentenceAndOffencesWithSDSPlus::toLatest)
      }
      else -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<SentenceAndOffenceWithReleaseArrangements>>() {})
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

  fun mapFixedTermRecallDetails(calculationRequest: CalculationRequest): FixedTermRecallDetails? {
    return objectMapper.convertValue(calculationRequest.fixedTermRecallDetails, FixedTermRecallDetails::class.java)
  }

  fun mapOffenderFinePayment(calculationRequest: CalculationRequest): List<OffenderFinePayment> {
    val reader = objectMapper.readerFor(object : TypeReference<List<OffenderFinePayment>>() {})
    return reader.readValue(calculationRequest.offenderFinePayments)
  }
}
