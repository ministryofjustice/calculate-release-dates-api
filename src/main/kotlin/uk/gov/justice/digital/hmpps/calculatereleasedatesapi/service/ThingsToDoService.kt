package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceAnalysis
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails

@Service
class ThingsToDoService(
  private val sentenceAndOffenceService: SentenceAndOffenceService,
  private val adjustmentsService: AdjustmentsService,
  private val prisonService: PrisonService,
) {

  fun getToDoList(prisonerId: String): ThingsToDo {
    val offenderDetails = prisonService.getOffenderDetail(prisonerId)
    val thingsToDo = if (isCalculationRequired(offenderDetails)) {
      listOf(ToDoType.CALCULATION_REQUIRED)
    } else {
      emptyList()
    }

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = thingsToDo,
    )
  }

  private fun isCalculationRequired(offenderDetails: PrisonerDetails): Boolean {
    val sentencesAndOffences = sentenceAndOffenceService.getSentencesAndOffences(offenderDetails.bookingId)
    val adjustments = adjustmentsService.getAnalyzedBookingAndSentenceAdjustments(offenderDetails.bookingId)

    return hasNewOrUpdatedSentences(sentencesAndOffences) ||
      hasNewBookingAdjustments(adjustments) ||
      hasNewSentenceAdjustments(adjustments)
  }

  private fun hasNewOrUpdatedSentences(sentencesAndOffences: List<AnalysedSentenceAndOffence>): Boolean {
    return sentencesAndOffences.any {
      it.sentenceAndOffenceAnalysis in listOf(SentenceAndOffenceAnalysis.NEW, SentenceAndOffenceAnalysis.UPDATED)
    }
  }

  private fun hasNewBookingAdjustments(adjustments: AnalyzedBookingAndSentenceAdjustments): Boolean {
    return adjustments.bookingAdjustments.any {
      it.analysisResult == AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW
    }
  }

  private fun hasNewSentenceAdjustments(adjustments: AnalyzedBookingAndSentenceAdjustments): Boolean {
    return adjustments.sentenceAdjustments.any {
      it.analysisResult == AnalyzedBookingAndSentenceAdjustmentAnalysisResult.NEW
    }
  }
}
