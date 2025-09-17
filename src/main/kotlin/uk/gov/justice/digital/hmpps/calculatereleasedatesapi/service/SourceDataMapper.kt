package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.SourceDataMissingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithSDSPlus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonApiExternalMovement

@Service
class SourceDataMapper(private val objectMapper: ObjectMapper) {

  fun getSourceData(calculationRequest: CalculationRequest): CalculationSourceData {
    val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { mapSentencesAndOffences(calculationRequest) }
    val prisonerDetails = calculationRequest.prisonerDetails?.let { mapPrisonerDetails(calculationRequest) }
    val bookingAndSentenceAdjustments = calculationRequest.adjustments?.let { mapBookingAndSentenceAdjustments(calculationRequest) }
    val returnToCustodyDate = calculationRequest.returnToCustodyDate?.let { mapReturnToCustodyDate(calculationRequest) }
    val finePayments = calculationRequest.offenderFinePayments?. let { mapOffenderFinePayment(calculationRequest) } ?: emptyList()
    val historicalTusedData = mapHistoricalTusedData(calculationRequest)
    val externalMovements = calculationRequest.externalMovements?.let { mapPrisonApiExternalMovement(calculationRequest) } ?: emptyList()

    if (sentenceAndOffences == null || prisonerDetails == null || bookingAndSentenceAdjustments == null) {
      throw SourceDataMissingException("Source data is missing from calculation ${calculationRequest.id}")
    }
    return CalculationSourceData(
      sentenceAndOffences,
      prisonerDetails,
      AdjustmentsSourceData(prisonApiData = bookingAndSentenceAdjustments),
      finePayments,
      returnToCustodyDate,
      null, // TODO CRS-2486 Store fixed term recall details to db.
      historicalTusedData,
      externalMovements,
    )
  }

  fun mapSentencesAndOffences(calculationRequest: CalculationRequest): List<SentenceAndOffenceWithReleaseArrangements> = when (calculationRequest.sentenceAndOffencesVersion) {
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

  fun mapPrisonerDetails(calculationRequest: CalculationRequest): PrisonerDetails = objectMapper.convertValue(calculationRequest.prisonerDetails, PrisonerDetails::class.java)

  fun mapBookingAndSentenceAdjustments(calculationRequest: CalculationRequest): BookingAndSentenceAdjustments {
    return when (calculationRequest.adjustmentsVersion) {
      // TODO temporary. We should be upgrading v0 to v1 here. But until frontend supports adjustments API use v0.
      1 -> {
        val adjustments = mapAdjustments(calculationRequest)
        return BookingAndSentenceAdjustments.downgrade(adjustments)
      }
      else -> objectMapper.convertValue(calculationRequest.adjustments, BookingAndSentenceAdjustments::class.java)
    }
  }

  fun mapAdjustments(calculationRequest: CalculationRequest): List<AdjustmentDto> = when (calculationRequest.adjustmentsVersion) {
    0 -> {
      val bookingAndSentenceAdjustments = mapBookingAndSentenceAdjustments(calculationRequest)
      val prisoner = mapPrisonerDetails(calculationRequest)
      bookingAndSentenceAdjustments.upgrade(prisoner)
    }
    else -> {
      val reader = objectMapper.readerFor(object : TypeReference<List<AdjustmentDto>>() {})
      reader.readValue(calculationRequest.adjustments)
    }
  }

  fun mapReturnToCustodyDate(calculationRequest: CalculationRequest): ReturnToCustodyDate = objectMapper.convertValue(calculationRequest.returnToCustodyDate, ReturnToCustodyDate::class.java)

  fun mapOffenderFinePayment(calculationRequest: CalculationRequest): List<OffenderFinePayment> {
    val reader = objectMapper.readerFor(object : TypeReference<List<OffenderFinePayment>>() {})
    return reader.readValue(calculationRequest.offenderFinePayments)
  }
  fun mapPrisonApiExternalMovement(calculationRequest: CalculationRequest): List<PrisonApiExternalMovement> {
    val reader = objectMapper.readerFor(object : TypeReference<List<PrisonApiExternalMovement>>() {})
    return reader.readValue(calculationRequest.externalMovements)
  }

  fun mapHistoricalTusedData(calculationRequest: CalculationRequest): HistoricalTusedData? {
    val historicalTusedDataNode = calculationRequest.inputData.findPath("historicalTusedData")
    if (historicalTusedDataNode.isMissingNode) {
      return null
    }
    return objectMapper.convertValue(historicalTusedDataNode, HistoricalTusedData::class.java)
  }
}
