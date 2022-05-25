package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.RECALL_TAGGED_BAIL
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConcurrentSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentencePart
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DateBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
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

fun transform(sentence: SentenceAndOffences, calculationUserInput: CalculationUserInput?): MutableList<out AbstractSentence> {
  // There shouldn't be multiple offences associated to a single sentence; however there are at the moment (NOMIS doesnt
  // guard against it) therefore if there are multiple offences associated with one sentence then each offence is being
  // treated as a separate sentence
  return sentence.offences.map { offendersOffence ->
    val isScheduleFifteenMaximumLife = if (calculationUserInput != null) {
      val matchingSentenceInput = calculationUserInput.sentenceCalculationUserInputs.find {
        it.sentenceSequence == sentence.sentenceSequence && it.offenceCode == offendersOffence.offenceCode
      }
      matchingSentenceInput?.isScheduleFifteenMaximumLife ?: offendersOffence.isScheduleFifteenMaximumLife
    } else {
      offendersOffence.isScheduleFifteenMaximumLife
    }

    val offence = Offence(
      committedAt = offendersOffence.offenceEndDate ?: offendersOffence.offenceStartDate!!,
      isScheduleFifteenMaximumLife = isScheduleFifteenMaximumLife
    )

    val consecutiveSentenceUUIDs = if (sentence.consecutiveToSequence != null)
      listOf(
        generateUUIDForSentence(sentence.bookingId, sentence.consecutiveToSequence)
      )
    else
      listOf()

    val sentenceCalculationType = SentenceCalculationType.from(sentence.sentenceCalculationType)!!
    if (sentenceCalculationType.sentenceClazz == StandardDeterminateSentence::class.java) {
      StandardDeterminateSentence(
        sentencedAt = sentence.sentenceDate,
        duration = transform(sentence.terms[0]),
        offence = offence,
        identifier = generateUUIDForSentence(sentence.bookingId, sentence.sentenceSequence),
        consecutiveSentenceUUIDs = consecutiveSentenceUUIDs,
        caseSequence = sentence.caseSequence,
        lineSequence = sentence.lineSequence,
        caseReference = sentence.caseReference,
        recallType = sentenceCalculationType.recallType
      )
    } else {
      val imprisonmentTerm = sentence.terms.first { it.code == "IMP" }
      val licenseTerm = sentence.terms.first { it.code == "LIC" }

      ExtendedDeterminateSentence(
        sentencedAt = sentence.sentenceDate,
        custodialDuration = transform(imprisonmentTerm),
        extensionDuration = transform(licenseTerm),
        automaticRelease = sentenceCalculationType == SentenceCalculationType.LASPO_AR,
        offence = offence,
        identifier = generateUUIDForSentence(sentence.bookingId, sentence.sentenceSequence),
        consecutiveSentenceUUIDs = consecutiveSentenceUUIDs,
        caseSequence = sentence.caseSequence,
        lineSequence = sentence.lineSequence,
        caseReference = sentence.caseReference,
        recallType = sentenceCalculationType.recallType
      )
    }
  }.toMutableList()
}

private fun transform(term: SentenceTerms): Duration {
  return Duration(
    mapOf(
      DAYS to term.days.toLong(),
      WEEKS to term.weeks.toLong(),
      MONTHS to term.months.toLong(),
      YEARS to term.years.toLong()
    )
  )
}

private fun generateUUIDForSentence(bookingId: Long, sequence: Int) =
  UUID.nameUUIDFromBytes(("$bookingId-$sequence").toByteArray())

fun transform(prisonerDetails: PrisonerDetails): Offender {
  return Offender(
    dateOfBirth = prisonerDetails.dateOfBirth,
    reference = prisonerDetails.offenderNo,
    isActiveSexOffender = prisonerDetails.alerts.any { pd ->
      pd.alertType == "S" &&
        pd.alertCode == "SOR" && // Sex offence register
        pd.dateCreated.isBeforeOrEqualTo(LocalDate.now()) &&
        (pd.dateExpires == null || pd.dateExpires.isAfter(LocalDate.now()))
    }
  )
}

fun transform(
  bookingAndSentenceAdjustments: BookingAndSentenceAdjustments,
  sentencesAndOffences: List<SentenceAndOffences>
): Adjustments {
  val adjustments = Adjustments()
  bookingAndSentenceAdjustments.bookingAdjustments.forEach {
    val adjustmentType = transform(it.type)
    if (adjustmentType != null) {
      adjustments.addAdjustment(
        adjustmentType,
        Adjustment(
          appliesToSentencesFrom = it.fromDate,
          numberOfDays = it.numberOfDays,
          fromDate = it.fromDate,
          toDate = it.toDate
        )
      )
    }
  }
  bookingAndSentenceAdjustments.sentenceAdjustments.forEach {
    val adjustmentType = transform(it.type)
    if (adjustmentType != null) {
      val sentence: SentenceAndOffences? = findSentenceForAdjustment(it, sentencesAndOffences)
      // If sentence is not present it could be that the adjustment is linked to an inactive sentence, which needs filtering out.
      if (sentence != null) {
        adjustments.addAdjustment(
          adjustmentType,
          Adjustment(
            appliesToSentencesFrom = sentence.sentenceDate,
            fromDate = it.fromDate,
            toDate = it.toDate,
            numberOfDays = it.numberOfDays
          )
        )
      }
    }
  }
  return adjustments
}

private fun findSentenceForAdjustment(adjustment: SentenceAdjustments, sentencesAndOffences: List<SentenceAndOffences>): SentenceAndOffences? {
  val sentence = sentencesAndOffences.find { adjustment.sentenceSequence == it.sentenceSequence }
  if (sentence == null) {
    return null
  } else {
    val recallType = SentenceCalculationType.from(sentence.sentenceCalculationType)!!.recallType
    if (listOf(
        SentenceAdjustmentType.REMAND,
        SentenceAdjustmentType.TAGGED_BAIL
      ).contains(adjustment.type) && recallType != null
    ) {
      val firstDeterminate = sentencesAndOffences.sortedBy { it.sentenceDate }
        .find { SentenceCalculationType.from(it.sentenceCalculationType)!!.recallType == null }
      if (firstDeterminate != null) {
        return firstDeterminate
      }
    }
    if (listOf(
        SentenceAdjustmentType.RECALL_SENTENCE_REMAND,
        SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL
      ).contains(adjustment.type) && recallType == null
    ) {
      val firstRecall = sentencesAndOffences.sortedBy { it.sentenceDate }
        .find { SentenceCalculationType.from(it.sentenceCalculationType)!!.recallType != null }
      if (firstRecall != null) {
        return firstRecall
      }
    }
    return sentence
  }
}

fun transform(sentenceAdjustmentType: SentenceAdjustmentType): AdjustmentType? {
  return when (sentenceAdjustmentType) {
    SentenceAdjustmentType.RECALL_SENTENCE_REMAND -> RECALL_REMAND
    SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL -> RECALL_TAGGED_BAIL
    SentenceAdjustmentType.REMAND -> REMAND
    SentenceAdjustmentType.TAGGED_BAIL -> TAGGED_BAIL
    SentenceAdjustmentType.UNUSED_REMAND -> null
  }
}

fun transform(bookingAdjustmentType: BookingAdjustmentType): AdjustmentType? {
  return when (bookingAdjustmentType) {
    BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED -> ADDITIONAL_DAYS_AWARDED
    BookingAdjustmentType.LAWFULLY_AT_LARGE -> null
    BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED -> RESTORATION_OF_ADDITIONAL_DAYS_AWARDED
    BookingAdjustmentType.SPECIAL_REMISSION -> null
    BookingAdjustmentType.UNLAWFULLY_AT_LARGE -> UNLAWFULLY_AT_LARGE
  }
}

fun transform(
  booking: Booking,
  username: String,
  calculationStatus: CalculationStatus,
  sourceData: PrisonApiSourceData,
  objectMapper: ObjectMapper,
  calculationUserInput: CalculationUserInput?,
  calculationFragments: CalculationFragments? = null
): CalculationRequest {
  return CalculationRequest(
    prisonerId = booking.offender.reference,
    bookingId = booking.bookingId,
    calculationStatus = calculationStatus.name,
    calculatedByUsername = username,
    inputData = objectToJson(booking, objectMapper),
    sentenceAndOffences = objectToJson(sourceData.sentenceAndOffences, objectMapper),
    prisonerDetails = objectToJson(sourceData.prisonerDetails, objectMapper),
    adjustments = objectToJson(sourceData.bookingAndSentenceAdjustments, objectMapper),
    returnToCustodyDate = if (sourceData.returnToCustodyDate != null) objectToJson(sourceData.returnToCustodyDate, objectMapper) else null,
    calculationRequestUserInputs = transform(calculationUserInput, sourceData),
    breakdownHtml = calculationFragments?.breakdownHtml
  )
}

fun transform(calculationUserInput: CalculationUserInput?, sourceData: PrisonApiSourceData): List<CalculationRequestUserInput> {
  if (calculationUserInput == null) {
    return emptyList()
  }
  return calculationUserInput.sentenceCalculationUserInputs.map {
    CalculationRequestUserInput(
      sentenceSequence = it.sentenceSequence,
      offenceCode = it.offenceCode,
      type = UserInputType.SCHEDULE_15_ATTRACTING_LIFE,
      userChoice = it.isScheduleFifteenMaximumLife,
      nomisMatches = sourceData.sentenceAndOffences.any { sentence -> sentence.sentenceSequence == it.sentenceSequence && sentence.offences.any { offence -> offence.offenceCode == it.offenceCode && offence.isScheduleFifteenMaximumLife == it.isScheduleFifteenMaximumLife } }
    )
  }
}

fun transform(calculationRequestUserInputs: List<CalculationRequestUserInput>): CalculationUserInput? {
  if (calculationRequestUserInputs.isEmpty()) {
    return null
  }
  return CalculationUserInput(
    calculationRequestUserInputs.map {
      SentenceCalculationUserInput(
        sentenceSequence = it.sentenceSequence,
        offenceCode = it.offenceCode,
        isScheduleFifteenMaximumLife = it.userChoice // we'll need to look at the type column when we have psc changes (dependent on UI design)
      )
    }
  )
}

fun objectToJson(subject: Any, objectMapper: ObjectMapper): JsonNode {
  return JacksonUtil.toJsonNode(objectMapper.writeValueAsString(subject))
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

fun transform(calculationRequest: CalculationRequest): CalculatedReleaseDates {
  return CalculatedReleaseDates(
    dates = calculationRequest.calculationOutcomes.associateBy(
      { ReleaseDateType.valueOf(it.calculationDateType) }, { it.outcomeDate }
    ).toMutableMap(),
    calculationRequestId = calculationRequest.id,
    calculationFragments = if (calculationRequest.breakdownHtml != null) CalculationFragments(calculationRequest.breakdownHtml) else null,
    bookingId = calculationRequest.bookingId,
    prisonerId = calculationRequest.prisonerId,
    calculationStatus = CalculationStatus.valueOf(calculationRequest.calculationStatus)
  )
}

fun transform(booking: Booking, breakdownByReleaseDateType: Map<ReleaseDateType, ReleaseDateCalculationBreakdown>, otherDates: Map<ReleaseDateType, LocalDate>): CalculationBreakdown {
  val concurrentSentences = booking.sentences.filter {
    booking.consecutiveSentences.none { consecutiveSentence ->
      consecutiveSentence.orderedSentences.contains(it)
    } &&
      it is StandardDeterminateSentence
  }.map { it as StandardDeterminateSentence }
  return CalculationBreakdown(
    concurrentSentences = concurrentSentences.map { sentence ->
      ConcurrentSentenceBreakdown(
        sentence.sentencedAt,
        sentence.duration.toString(),
        sentence.sentenceCalculation.numberOfDaysToSentenceExpiryDate,
        extractDates(sentence),
        sentence.lineSequence!!,
        sentence.caseSequence!!,
        sentence.caseReference
      )
    }.sortedWith(compareBy({ it.caseSequence }, { it.lineSequence })),
    consecutiveSentence = if (booking.consecutiveSentences.isNotEmpty()) {
      if (booking.consecutiveSentences.size == 1) {
        val consecutiveSentence = booking.consecutiveSentences.filter { it is StandardDeterminateConsecutiveSentence }[0] as StandardDeterminateConsecutiveSentence
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
              sentencePart.caseReference,
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
    },
    breakdownByReleaseDateType = breakdownByReleaseDateType,
    otherDates = otherDates
  )
}

private fun combineDuration(standardConsecutiveSentence: StandardDeterminateConsecutiveSentence): Duration {
  return standardConsecutiveSentence.orderedSentences
    .map { it.duration }
    .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
}

fun transform(calculation: CalculatedReleaseDates) =
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
      calculation.effectiveSentenceLength?.years,
      calculation.effectiveSentenceLength?.months,
      calculation.effectiveSentenceLength?.days
    )
  )

private fun extractDates(sentence: CalculableSentence): Map<ReleaseDateType, DateBreakdown> {
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
    sentenceCalculation.unadjustedDeterminateReleaseDate,
    sentenceCalculation.adjustedDeterminateReleaseDate,
    sentenceCalculation.numberOfDaysToDeterminateReleaseDate.toLong()
  )

  return dates
}
