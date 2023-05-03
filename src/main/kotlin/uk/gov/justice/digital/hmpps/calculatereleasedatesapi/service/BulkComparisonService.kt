package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService

class BulkComparisonService(private val prisonService: PrisonService,
                            private val calculationUserQuestionService: CalculationUserQuestionService,
                            private val bookingService: BookingService,
                            private val validationService: ValidationService,
                            private val calculationService: CalculationService) {
  fun compare(nomisIds: List<String>): List<ComparisonResult> {
    return nomisIds.map { nomisId -> Pair(nomisId, prisonService.getOffenderDetail(nomisId)) }
      .map {
        val bookingId = it.second.bookingId
        val nomisDates = prisonService.getSentenceDetail(bookingId)
        val keyDates = prisonService.getOffenderKeyDates(bookingId)
        val sentenceAndOffences = prisonService.getSentencesAndOffences(bookingId)
        val adjustments = prisonService.getBookingAndSentenceAdjustments(bookingId)
        val returnToCustody = if (sentenceAndOffences.any { s -> SentenceCalculationType.from(s.sentenceCalculationType).isFixedTermRecall }) {
          prisonService.getReturnToCustodyDate(bookingId)
        } else {
          null
        }
        val finePayments = if (sentenceAndOffences.any { s -> SentenceCalculationType.from(s.sentenceCalculationType).sentenceClazz == AFineSentence::class.java }) {
          prisonService.getOffenderFinePayments(bookingId)
        } else {
          listOf()
        }
        val questions = if (sentenceAndOffences.any { s -> SentenceCalculationType.from(s.sentenceCalculationType).sentenceClazz == StandardDeterminateSentence::class.java }) {
          calculationUserQuestionService.getQuestionsForSentences(it.second.offenderNo)
        } else {
          null
        }
        val sourceData = prisonService.getPrisonApiSourceData(it.first, true)
        val calculationUserInputs = CalculationUserInputs(calculateErsed = keyDates.earlyRemovalSchemeEligibilityDate != null, useOffenceIndicators = true)
        val booking = bookingService.getBooking(sourceData, calculationUserInputs)
        val messages = validationService.validateBeforeCalculation(sourceData, calculationUserInputs).toMutableList() // Validation stage 1 of 3
        messages += validationService.validateBeforeCalculation(booking) // Validation stage 2 of 4
        val bookingAfterCalculation = calculationService.calculate(booking) // Validation stage 3 of 4
        messages += validationService.validateBookingAfterCalculation(bookingAfterCalculation) // Validation stage 4 of 4
        if (messages.size == 0) {
          transform(booking, messages.toList())
        }
        val (workingBooking, bookingCalculation) = calculationService.calculateReleaseDates(booking)
        val calculationBreakdown = transform(workingBooking, bookingCalculation.breakdownByReleaseDateType, bookingCalculation.otherDates)
        transform(it.first, it.second, workingBooking, bookingCalculation, calculationBreakdown, nomisDates, keyDates, sentenceAndOffences, adjustments, returnToCustody, finePayments, questions)
      }
  }



}