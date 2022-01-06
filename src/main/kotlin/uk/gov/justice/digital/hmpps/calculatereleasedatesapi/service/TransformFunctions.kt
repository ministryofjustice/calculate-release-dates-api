package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.TAGGED_BAIL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.UNLAWFULLY_AT_LARGE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.DPRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ETD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.MTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.NPD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConcurrentSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentencePart
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DateBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtractableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

/*
** Functions which transform entities objects into their model equivalents.
** Sometimes a pass-thru but very useful when objects need to be altered or enriched
*/

fun transform(sentence: SentenceAndOffences): MutableList<Sentence> {
  // There shouldn't be multiple offences associated to a single sentence; however there are at the moment (NOMIS doesnt
  // guard against it) therefore if there are multiple offences associated with one sentence then each offence is being
  // treated as a separate sentence
  return sentence.offences.map { offendersOffence ->
    val offence = Offence(
      committedAt = offendersOffence.offenceEndDate ?: offendersOffence.offenceStartDate,
      isScheduleFifteen = offendersOffence.indicators.any { it == OffenderOffence.SCHEDULE_15_INDICATOR }
    )
    val duration = Duration()
    duration.append(sentence.days.toLong(), DAYS)
    duration.append(sentence.weeks.toLong(), WEEKS)
    duration.append(sentence.months.toLong(), MONTHS)
    duration.append(sentence.years.toLong(), YEARS)
    val consecutiveSentenceUUIDs = if (sentence.consecutiveToSequence != null)
      listOf(
        generateUUIDForSentence(sentence.bookingId, sentence.consecutiveToSequence)
      )
    else
      listOf()

    Sentence(
      sentencedAt = sentence.sentenceDate,
      duration = duration,
      offence = offence,
      identifier = generateUUIDForSentence(sentence.bookingId, sentence.sentenceSequence),
      consecutiveSentenceUUIDs = consecutiveSentenceUUIDs,
      caseSequence = sentence.caseSequence,
      lineSequence = sentence.lineSequence
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
  adjustments[ADDITIONAL_DAYS_AWARDED] = sentenceAdjustments.additionalDaysAwarded
  adjustments[RESTORATION_OF_ADDITIONAL_DAYS_AWARDED] = sentenceAdjustments.restoredAdditionalDaysAwarded
  return adjustments
}

fun transform(
  booking: Booking,
  username: String,
  calculationStatus: CalculationStatus,
  objectMapper: ObjectMapper
): CalculationRequest {
  return CalculationRequest(
    prisonerId = booking.offender.reference,
    bookingId = booking.bookingId,
    calculationStatus = calculationStatus.name,
    calculatedByUsername = username,
    inputData = bookingToJson(booking, objectMapper)
  )
}

fun bookingToJson(booking: Booking, objectMapper: ObjectMapper): JsonNode {
  return JacksonUtil.toJsonNode(objectMapper.writeValueAsString(booking))
}

fun transform(
  calculationRequest: CalculationRequest,
  releaseDateType: ReleaseDateType,
  date: LocalDate
): CalculationOutcome {
  return CalculationOutcome(
    calculationRequestId = calculationRequest.id,
    outcomeDate = date,
    calculationDateType = releaseDateType.name,
  )
}

fun transform(calculationRequest: CalculationRequest): BookingCalculation {
  return BookingCalculation(
    dates = calculationRequest.calculationOutcomes.associateBy(
      { ReleaseDateType.valueOf(it.calculationDateType) }, { it.outcomeDate }
    ).toMutableMap(),
    calculationRequestId = calculationRequest.id
  )
}

fun transform(booking: Booking): CalculationBreakdown {
  val concurrentSentences = booking.sentences.filter {
    booking.consecutiveSentences.none { consecutiveSentence ->
      consecutiveSentence.orderedSentences.contains(it)
    }
  }
  return CalculationBreakdown(
    concurrentSentences.map { sentence ->
      ConcurrentSentenceBreakdown(
        sentence.sentencedAt,
        sentence.duration.toString(),
        sentence.sentenceCalculation.numberOfDaysToSentenceExpiryDate,
        extractDates(sentence),
        sentence.lineSequence!!,
        sentence.caseSequence!!,
      )
    }.sortedWith(compareBy({ it.caseSequence }, { it.lineSequence })),
    if (booking.consecutiveSentences.isNotEmpty()) {
      if (booking.consecutiveSentences.size == 1) {
        val consecutiveSentence = booking.consecutiveSentences[0]
        ConsecutiveSentenceBreakdown(
          consecutiveSentence.sentencedAt,
          combineDuration(consecutiveSentence).toString(),
          consecutiveSentence.sentenceCalculation.numberOfDaysToSentenceExpiryDate,
          extractDates(consecutiveSentence),
          consecutiveSentence.orderedSentences.map { sentencePart ->
            val originalSentence = booking.sentences.find { it.identifier == sentencePart.identifier }!!
            val consecutiveToUUID =
              if (originalSentence.consecutiveSentenceUUIDs.isNotEmpty()) originalSentence.consecutiveSentenceUUIDs[0]
              else null
            val consecutiveToSentence =
              if (consecutiveToUUID != null) booking.sentences.find { it.identifier == consecutiveToUUID }!!
              else null
            ConsecutiveSentencePart(
              sentencePart.lineSequence!!,
              sentencePart.caseSequence!!,
              sentencePart.duration.toString(),
              sentencePart.sentenceCalculation.numberOfDaysToSentenceExpiryDate,
              consecutiveToSentence?.lineSequence,
              consecutiveToSentence?.caseSequence,
            )
          }.sortedWith(compareBy({ it.caseSequence }, { it.lineSequence }))
        )
      } else {
        // Multiple chains of consecutive sentences. This is currently unsupported in calc breakdown.
        throw UnsupportedCalculationBreakdown("Multiple chains of consecutive sentences are not supported by calculation breakdown")
      }
    } else {
      null
    }
  )
}

private fun combineDuration(consecutiveSentence: ConsecutiveSentence): Duration {
  val duration = Duration()
  consecutiveSentence.orderedSentences.forEach {
    duration.appendAll(it.duration.durationElements)
  }
  return duration
}

fun transform(calculation: BookingCalculation) =
  OffenderKeyDates(
    conditionalReleaseDate = calculation.dates[CRD],
    licenceExpiryDate = calculation.dates[SLED] ?: calculation.dates[LED],
    sentenceExpiryDate = calculation.dates[SLED] ?: calculation.dates[SED],
    automaticReleaseDate = calculation.dates[ARD],
    dtoPostRecallReleaseDate = calculation.dates[DPRRD],
    earlyTermDate = calculation.dates[ETD],
    homeDetentionCurfewEligibilityDate = calculation.dates[HDCED],
    lateTermDate = calculation.dates[LTD],
    midTermDate = calculation.dates[MTD],
    nonParoleDate = calculation.dates[NPD],
    paroleEligibilityDate = calculation.dates[PED],
    postRecallReleaseDate = calculation.dates[PRRD],
    topupSupervisionExpiryDate = calculation.dates[TUSED],
    effectiveSentenceEndDate = calculation.dates[ESED],
    sentenceLength = String.format(
      "%02d/%02d/%02d",
      calculation.effectiveSentenceLength.years,
      calculation.effectiveSentenceLength.months,
      calculation.effectiveSentenceLength.days
    )
  )

private fun extractDates(sentence: ExtractableSentence): Map<ReleaseDateType, DateBreakdown> {
  val dates: MutableMap<ReleaseDateType, DateBreakdown> = mutableMapOf()
  val sentenceCalculation = sentence.sentenceCalculation

  if (sentence.releaseDateTypes.contains(SLED)) {
    dates[SLED] = DateBreakdown(
      sentenceCalculation.unadjustedExpiryDate,
      sentenceCalculation.adjustedExpiryDate,
      sentenceCalculation.numberOfDaysToSentenceExpiryDate.toLong()
    )
  } else {
    dates[SED] = DateBreakdown(
      sentenceCalculation.unadjustedExpiryDate,
      sentenceCalculation.adjustedExpiryDate,
      sentenceCalculation.numberOfDaysToSentenceExpiryDate.toLong()
    )
  }
  dates[sentence.getReleaseDateType()] = DateBreakdown(
    sentenceCalculation.unadjustedReleaseDate,
    sentenceCalculation.adjustedReleaseDate,
    sentenceCalculation.numberOfDaysToReleaseDate.toLong()
  )

  return dates
}
