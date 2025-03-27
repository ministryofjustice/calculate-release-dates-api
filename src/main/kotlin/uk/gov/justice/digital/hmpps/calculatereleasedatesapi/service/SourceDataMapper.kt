package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithSDSPlus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments

@Service
class SourceDataMapper(private val objectMapper: ObjectMapper) {

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
    return when (calculationRequest.adjustmentsVersion) {
      // TODO temporary. We should be upgrading v0 to v1 here. But until frontend supports adjustments API use v0.
      1 -> {
        val reader = objectMapper.readerFor(object : TypeReference<List<AdjustmentDto>>() {})
        val adjustments = reader.readValue<List<AdjustmentDto>>(calculationRequest.adjustments)
        return BookingAndSentenceAdjustments.downgrade(adjustments)
      }
      else -> objectMapper.convertValue(calculationRequest.adjustments, BookingAndSentenceAdjustments::class.java)
    }
  }

  fun mapReturnToCustodyDate(calculationRequest: CalculationRequest): ReturnToCustodyDate {
    return objectMapper.convertValue(calculationRequest.returnToCustodyDate, ReturnToCustodyDate::class.java)
  }

  fun mapOffenderFinePayment(calculationRequest: CalculationRequest): List<OffenderFinePayment> {
    val reader = objectMapper.readerFor(object : TypeReference<List<OffenderFinePayment>>() {})
    return reader.readValue(calculationRequest.offenderFinePayments)
  }

  fun mapHistoricalTusedData(calculationRequest: CalculationRequest): HistoricalTusedData? {
    val historicalTusedDataNode = calculationRequest.inputData.findPath("historicalTusedData")
    if (historicalTusedDataNode.isMissingNode) {
      return null
    }
    return objectMapper.convertValue(historicalTusedDataNode, HistoricalTusedData::class.java)
  }
}
