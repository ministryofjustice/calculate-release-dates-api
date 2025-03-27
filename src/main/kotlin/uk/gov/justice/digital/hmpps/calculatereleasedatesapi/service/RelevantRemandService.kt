package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate

@Service
class RelevantRemandService(
  private val prisonService: PrisonService,
  private val calculationService: CalculationService,
  private val validationService: ValidationService,
  private val bookingService: BookingService,
  private val calculationSourceDataService: CalculationSourceDataService,
) {

  fun relevantRemandCalculation(prisonerId: String, request: RelevantRemandCalculationRequest): RelevantRemandCalculationResult {
    val prisoner = prisonService.getOffenderDetail(prisonerId).copy(
      bookingId = request.sentence.bookingId,
    )
    val sourceData = filterSentencesAndAdjustmentsForRelevantRemandCalc(calculationSourceDataService.getCalculationSourceData(prisoner, InactiveDataOptions.overrideToIncludeInactiveData()), request)
    val calculationUserInputs = CalculationUserInputs(useOffenceIndicators = true)

    var validationMessages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs)
    if (validationMessages.isNotEmpty()) {
      return RelevantRemandCalculationResult(
        validationMessages = validationMessages,
      )
    }
    val booking = bookingService.getBooking(sourceData, calculationUserInputs)
    validationMessages = validationService.validateBeforeCalculation(booking)
    if (validationMessages.isNotEmpty()) {
      return RelevantRemandCalculationResult(
        validationMessages = validationMessages,
      )
    }

    val result = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculationResult = result.calculationResult
    val releaseDateTypes = listOf(ReleaseDateType.CRD, ReleaseDateType.ARD, ReleaseDateType.MTD)

    var releaseDate = calculationResult.dates.filter { releaseDateTypes.contains(it.key) }.minOfOrNull { it.value }
    var postRecallReleaseDate: LocalDate? = null
    if (releaseDate == null && calculationResult.dates.contains(ReleaseDateType.PRRD)) {
      postRecallReleaseDate = calculationResult.dates[ReleaseDateType.PRRD]
      releaseDate = result.sentences.find { it.sentenceCalculation.adjustedPostRecallReleaseDate == postRecallReleaseDate }!!.sentenceCalculation.adjustedDeterminateReleaseDate
    }
    return RelevantRemandCalculationResult(
      releaseDate = releaseDate,
      postRecallReleaseDate = postRecallReleaseDate,

    )
  }

  private fun filterSentencesAndAdjustmentsForRelevantRemandCalc(sourceData: CalculationSourceData, request: RelevantRemandCalculationRequest): CalculationSourceData {
    return sourceData.copy(
      sentenceAndOffences = sourceData.sentenceAndOffences.filter { it.sentenceDate.isBeforeOrEqualTo(request.calculateAt) },
      bookingAndSentenceAdjustments = sourceData.bookingAndSentenceAdjustments.fold(
        { filterAdjustmentsFromPrisonApi(it, sourceData, request) },
        { filterAdjustmentsFromAdjustmentsApi(it, sourceData, request) },
      ),
    )
  }

  private fun filterAdjustmentsFromPrisonApi(
    bookingAndSentenceAdjustments: BookingAndSentenceAdjustments,
    sourceData: CalculationSourceData,
    request: RelevantRemandCalculationRequest,
  ): Either<BookingAndSentenceAdjustments, List<AdjustmentDto>> {
    return bookingAndSentenceAdjustments.copy(
      sentenceAdjustments = bookingAndSentenceAdjustments.sentenceAdjustments.filter { !listOf(SentenceAdjustmentType.REMAND, SentenceAdjustmentType.RECALL_SENTENCE_REMAND, SentenceAdjustmentType.UNUSED_REMAND).contains(it.type) } +
        request.relevantRemands.map {
          val sentence = findSentence(sourceData.sentenceAndOffences, it.sentenceSequence)
          val adjustmentType: SentenceAdjustmentType =
            if (sentence != null && SentenceCalculationType.isCalculable(sentence.sentenceCalculationType)) {
              val sentenceType = SentenceCalculationType.from(sentence.sentenceCalculationType)
              if (sentenceType.recallType != null) {
                SentenceAdjustmentType.RECALL_SENTENCE_REMAND
              } else {
                SentenceAdjustmentType.REMAND
              }
            } else {
              SentenceAdjustmentType.REMAND
            }
          SentenceAdjustment(it.sentenceSequence, true, it.from, it.to, it.days, adjustmentType)
        },
    ).left()
  }

  private fun filterAdjustmentsFromAdjustmentsApi(
    adjustments: List<AdjustmentDto>,
    sourceData: CalculationSourceData,
    request: RelevantRemandCalculationRequest,
  ): Either<BookingAndSentenceAdjustments, List<AdjustmentDto>> {
    return (
      adjustments.filter { !listOf(AdjustmentDto.AdjustmentType.REMAND, AdjustmentDto.AdjustmentType.UNUSED_DEDUCTIONS).contains(it.adjustmentType) } +
        request.relevantRemands.map {
          AdjustmentDto(
            bookingId = request.sentence.bookingId,
            fromDate = it.from,
            toDate = it.to,
            effectiveDays = it.days,
            adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
            person = sourceData.prisonerDetails.offenderNo,
            sentenceSequence = it.sentenceSequence,
          )
        }
      ).right()
  }

  private fun findSentence(sentenceAndOffences: List<SentenceAndOffence>, sentenceSequence: Int): SentenceAndOffence? {
    return sentenceAndOffences.find { it.sentenceSequence == sentenceSequence }
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
