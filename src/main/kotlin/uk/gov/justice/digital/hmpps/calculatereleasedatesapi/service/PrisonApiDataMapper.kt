package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences

@Service
class PrisonApiDataMapper(private val objectMapper: ObjectMapper) {

  fun mapSentencesAndOffences(calculationRequest: CalculationRequest): List<SentenceAndOffences> {
    if (calculationRequest.sentenceAndOffencesVersion == 0) {
      val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiDataVersions.Version0.SentenceAndOffences>>() {})
      val sentencesAndOffences: List<PrisonApiDataVersions.Version0.SentenceAndOffences> = reader.readValue(calculationRequest.sentenceAndOffences)
      return sentencesAndOffences.map { it.toLatest() }
    }
    val reader = objectMapper.readerFor(object : TypeReference<List<SentenceAndOffences>>() {})
    return reader.readValue(calculationRequest.sentenceAndOffences)
  }

  fun mapPrisonerDetails(calculationRequest: CalculationRequest): PrisonerDetails {
    return objectMapper.convertValue(calculationRequest.prisonerDetails, PrisonerDetails::class.java)
  }

  fun mapBookingAndSentenceAdjustments(calculationRequest: CalculationRequest): BookingAndSentenceAdjustments {
    return objectMapper.convertValue(calculationRequest.adjustments, BookingAndSentenceAdjustments::class.java)
  }
}
