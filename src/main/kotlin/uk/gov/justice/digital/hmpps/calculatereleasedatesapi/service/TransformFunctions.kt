package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_AWARDED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType.ADDITIONAL_DAYS_SERVED
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConcurrentSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentenceBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentencePart
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DateBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceDiagram
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceDiagramRow
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceDiagramRowSection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
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

fun transform(sentence: SentenceAndOffences, calculationUserInputs: CalculationUserInputs?): MutableList<out AbstractSentence> {
  // There shouldn't be multiple offences associated to a single sentence; however there are at the moment (NOMIS doesnt
  // guard against it) therefore if there are multiple offences associated with one sentence then each offence is being
  // treated as a separate sentence
  return sentence.offences.map { offendersOffence ->
    val offence = if (calculationUserInputs != null) {
      val matchingSentenceInput = calculationUserInputs.sentenceCalculationUserInputs.find {
        it.sentenceSequence == sentence.sentenceSequence && it.offenceCode == offendersOffence.offenceCode
      }
      Offence(
        committedAt = offendersOffence.offenceEndDate ?: offendersOffence.offenceStartDate!!,
        isScheduleFifteenMaximumLife = matchingSentenceInput?.userChoice == true && matchingSentenceInput.userInputType == UserInputType.ORIGINAL,
        isPcscSds = matchingSentenceInput?.userChoice == true && matchingSentenceInput.userInputType == UserInputType.FOUR_TO_UNDER_SEVEN,
        isPcscSec250 = matchingSentenceInput?.userChoice == true && matchingSentenceInput.userInputType == UserInputType.SECTION_250,
        isPcscSdsPlus = matchingSentenceInput?.userChoice == true && matchingSentenceInput.userInputType == UserInputType.UPDATED,
      )
    } else {
      Offence(
        committedAt = offendersOffence.offenceEndDate ?: offendersOffence.offenceStartDate!!,
        isScheduleFifteenMaximumLife = offendersOffence.isScheduleFifteenMaximumLife,
        isPcscSds = offendersOffence.isPcscSds,
        isPcscSec250 = offendersOffence.isPcscSec250,
        isPcscSdsPlus = offendersOffence.isPcscSdsPlus,
      )
    }

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
        recallType = sentenceCalculationType.recallType,
        section250 = sentenceCalculationType == SentenceCalculationType.SEC250 || sentenceCalculationType == SentenceCalculationType.SEC250_ORA
      )
    } else {
      val imprisonmentTerm = sentence.terms.first { it.code == SentenceTerms.IMPRISONMENT_TERM_CODE }
      val licenseTerm = sentence.terms.first { it.code == SentenceTerms.LICENCE_TERM_CODE }

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
    isActiveSexOffender = prisonerDetails.activeAlerts().any {
      it.alertType == "S" &&
        it.alertCode == "SOR" // Sex offence register
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
  calculationUserInputs: CalculationUserInputs?,
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
    calculationRequestUserInputs = transform(calculationUserInputs, sourceData),
    breakdownHtml = calculationFragments?.breakdownHtml
  )
}

fun transform(calculationUserInputs: CalculationUserInputs?, sourceData: PrisonApiSourceData): List<CalculationRequestUserInput> {
  if (calculationUserInputs == null) {
    return emptyList()
  }
  return calculationUserInputs.sentenceCalculationUserInputs.map {
    CalculationRequestUserInput(
      sentenceSequence = it.sentenceSequence,
      offenceCode = it.offenceCode,
      type = it.userInputType,
      userChoice = it.userChoice,
      nomisMatches = sourceData.sentenceAndOffences.any { sentence -> sentence.sentenceSequence == it.sentenceSequence && sentence.offences.any { offence -> offence.offenceCode == it.offenceCode && offenceMatchesChoice(offence, it.userInputType, it.userChoice) } }
    )
  }
}

fun offenceMatchesChoice(offence: OffenderOffence, userInputType: UserInputType, userChoice: Boolean): Boolean {
  return when (userInputType) {
    UserInputType.ORIGINAL -> offence.isScheduleFifteenMaximumLife == userChoice
    UserInputType.FOUR_TO_UNDER_SEVEN -> offence.isPcscSds == userChoice
    UserInputType.SECTION_250 -> offence.isPcscSec250 == userChoice
    UserInputType.UPDATED -> offence.isPcscSdsPlus == userChoice
  }
}

fun transform(calculationRequestUserInputs: List<CalculationRequestUserInput>): CalculationUserInputs? {
  if (calculationRequestUserInputs.isEmpty()) {
    return null
  }
  return CalculationUserInputs(
    calculationRequestUserInputs.map {
      CalculationSentenceUserInput(
        sentenceSequence = it.sentenceSequence,
        offenceCode = it.offenceCode,
        userInputType = it.type,
        userChoice = it.userChoice,
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
        sentence.lineSequence ?: 0,
        sentence.caseSequence ?: 0,
        sentence.caseReference
      )
    }.sortedWith(compareBy({ it.caseSequence }, { it.lineSequence })),
    consecutiveSentence = if (booking.consecutiveSentences.isNotEmpty()) {
      if (booking.consecutiveSentences.size == 1) {
        val consecutiveSentence = booking.consecutiveSentences.filter { it is ConsecutiveSentence }[0] as ConsecutiveSentence
        ConsecutiveSentenceBreakdown(
          consecutiveSentence.sentencedAt,
          consecutiveSentence.getCombinedDuration().toString(),
          consecutiveSentence.sentenceCalculation.numberOfDaysToSentenceExpiryDate,
          extractDates(consecutiveSentence),
          consecutiveSentence.orderedSentences.map { sentencePart ->
            sentencePart as AbstractSentence
            val originalSentence = booking.sentences.find { it.identifier == sentencePart.identifier }!!
            val consecutiveToUUID =
              if (originalSentence.consecutiveSentenceUUIDs.isNotEmpty()) originalSentence.consecutiveSentenceUUIDs[0]
              else null
            val consecutiveToSentence =
              if (consecutiveToUUID != null) booking.sentences.find { it.identifier == consecutiveToUUID }!!
              else null
            ConsecutiveSentencePart(
              sentencePart.lineSequence ?: 0,
              sentencePart.caseSequence ?: 0,
              sentencePart.caseReference,
              if (sentencePart is StandardDeterminateSentence) sentencePart.duration.toString() else "",
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

fun transform(booking: Booking): SentenceDiagram {
  val orderedAdjustmentTypes = listOf(REMAND, RECALL_REMAND, TAGGED_BAIL, RECALL_TAGGED_BAIL, UNLAWFULLY_AT_LARGE, ADDITIONAL_DAYS_SERVED)
  val adjustmentRows = orderedAdjustmentTypes.map {
    booking.adjustments.getOrEmptyList(it).mapNotNull { adjustment ->
      if ((adjustment.fromDate != null && adjustment.toDate != null) || it == ADDITIONAL_DAYS_SERVED) {
        val start: LocalDate
        val end: LocalDate
        if (it == ADDITIONAL_DAYS_SERVED) {
          start = adjustment.appliesToSentencesFrom
          end = adjustment.appliesToSentencesFrom.plusDays(adjustment.numberOfDays.toLong())
        } else {
          start = adjustment.fromDate!!
          end = adjustment.toDate!!
        }
        SentenceDiagramRow("${it.text} ${adjustment.numberOfDays} days", listOf(SentenceDiagramRowSection(start, end, null)))
      } else {
        null
      }
    }
  }.flatten()
  val sentenceRows = booking.getAllExtractableSentences()
    .map {
      when (it) {
        is AbstractSentence -> {
          SentenceDiagramRow(
            "Court case ${it.caseSequence} sentence ${it.lineSequence}",
            listOf(
              SentenceDiagramRowSection(start = it.sentencedAt, end = it.sentenceCalculation.releaseDate, description = "Release date"),
              SentenceDiagramRowSection(start = it.sentenceCalculation.releaseDate, end = it.sentenceCalculation.expiryDate!!, description = "Expiry date")
            )
          )
        }
        is SingleTermSentence -> {
          SentenceDiagramRow(
            "Single term sentence",
            listOf(
              SentenceDiagramRowSection(start = it.sentencedAt, end = it.sentenceCalculation.releaseDate, description = "Release date"),
              SentenceDiagramRowSection(start = it.sentenceCalculation.releaseDate, end = it.sentenceCalculation.expiryDate!!, description = "Expiry date")
            )
          )
        }
        is ConsecutiveSentence -> {
          val nameOfSentence = it.orderedSentences.joinToString { sentence ->
            (sentence as AbstractSentence)
            "Court case ${sentence.caseSequence} sentence ${sentence.lineSequence}"
          }
          SentenceDiagramRow(
            "Consecutive sentence $nameOfSentence",
            listOf(
              SentenceDiagramRowSection(start = it.sentencedAt, end = it.sentenceCalculation.releaseDate, description = "Release date"),
              SentenceDiagramRowSection(start = it.sentenceCalculation.releaseDate, end = it.sentenceCalculation.expiryDate!!, description = "Expiry date")
            )
          )
        }
        else -> {
          throw UnsupportedCalculationBreakdown("unsupported")
        }
      }
    }
  return SentenceDiagram(
    rows = adjustmentRows + sentenceRows
  )
}
