package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.TestData as EntityTestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData as ModelTestData

/*
** Functions which transform entities objects into their model equivalents.
** Sometimes a pass-thru but very useful when objects need to be altered or enriched
*/

fun transform(testData: EntityTestData): ModelTestData {
  return ModelTestData(
    key = testData.key,
    value = testData.value
  )
}

fun transform(sentence: SentenceAndOffences): MutableList<Sentence> {
  // There shouldn't be multiple offences associated to a single sentence; however there are at the moment (NOMIS doesnt
  // guard against it) therefore if there are multiple offences associated with one sentence then each offence is being
  // treated as a separate sentence
  return sentence.offences.map { offendersOffence ->
    val offence = Offence(startedAt = offendersOffence.offenceDate)
    val duration = Duration()
    duration.append(sentence.days.toLong(), DAYS)
    duration.append(sentence.months.toLong(), MONTHS)
    duration.append(sentence.years.toLong(), YEARS)
    val consecutiveSentenceUUIDs = if (sentence.consecutiveToSequence != null)
      mutableListOf(
        generateUUIDForSentence(sentence.bookingId, sentence.consecutiveToSequence)
      )
    else
      mutableListOf()

    Sentence(
      sentencedAt = sentence.sentenceDate,
      duration = duration,
      offence = offence,
      identifier = generateUUIDForSentence(sentence.bookingId, sentence.sentenceSequence),
      consecutiveSentenceUUIDs = consecutiveSentenceUUIDs
    )
  }.toMutableList()
}

private fun generateUUIDForSentence(bookingId: Long, sequence: Int) =
  UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray())

fun transform(prisonerDetails: PrisonerDetails): Offender {
  return Offender(
    name = prisonerDetails.firstName + ' ' + prisonerDetails.lastName,
    dateOfBirth = prisonerDetails.dateOfBirth,
    reference = prisonerDetails.offenderNo,
  )
}

fun transform(sentenceAdjustments: SentenceAdjustments): MutableMap<AdjustmentType, Int> {
  val adjustments = mutableMapOf<AdjustmentType, Int>()
  adjustments[REMAND] = sentenceAdjustments.remand
  adjustments[TAGGED_BAIL] = sentenceAdjustments.taggedBail
  adjustments[UNLAWFULLY_AT_LARGE] = sentenceAdjustments.unlawfullyAtLarge
  return adjustments
}

fun transform(booking: Booking, username: String, calculationStatus: CalculationStatus): CalculationRequest {
  val mapper = ObjectMapper()
  mapper.registerModule(JavaTimeModule())
  return CalculationRequest(
    prisonerId = booking.offender.reference,
    bookingId = booking.bookingId,
    calculationStatus = calculationStatus.name,
    calculatedByUsername = username,
    inputData = JacksonUtil.toJsonNode(mapper.writeValueAsString(booking))
  )
}

fun transform(calculationRequest: CalculationRequest, sentenceType: SentenceType, date: LocalDate): CalculationOutcome {
  return CalculationOutcome(
    calculationRequest = calculationRequest,
    outcomeDate = date,
    calculationDateType = sentenceType.name,
  )
}
