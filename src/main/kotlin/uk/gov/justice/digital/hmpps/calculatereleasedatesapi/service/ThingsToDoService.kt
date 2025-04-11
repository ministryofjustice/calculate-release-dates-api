package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAndOffence
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
    val adjustments = adjustmentsService.getAnalysedBookingAndSentenceAdjustments(offenderDetails.bookingId)

    return hasNewOrUpdatedSentences(sentencesAndOffences) ||
      hasNewBookingAdjustments(adjustments) ||
      hasNewSentenceAdjustments(adjustments)
  }

  private fun hasNewOrUpdatedSentences(sentencesAndOffences: List<AnalysedSentenceAndOffence>): Boolean {
    return sentencesAndOffences.any {
      it.sentenceAndOffenceAnalysis in listOf(SentenceAndOffenceAnalysis.NEW, SentenceAndOffenceAnalysis.UPDATED)
    }
  }

  private fun hasNewBookingAdjustments(adjustments: AnalysedBookingAndSentenceAdjustments): Boolean {
    return adjustments.bookingAdjustments.any {
      it.analysisResult == AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
    }
  }

  private fun hasNewSentenceAdjustments(adjustments: AnalysedBookingAndSentenceAdjustments): Boolean {
    return adjustments.sentenceAdjustments.any {
      it.analysisResult == AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW
    }
  }
}
