package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceQuestion
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserQuestions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType.FOUR_TO_UNDER_SEVEN
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType.ORIGINAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType.SECTION_250
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType.UPDATED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.PCSC_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ImportantDates.SDS_PLUS_COMMENCEMENT_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.EnumSet

@Service
class CalculationUserQuestionService (
  private val prisonService: PrisonService,
  ){
  val postPcscCalcTypes: Map<UserInputType, EnumSet<SentenceCalculationType>> = mapOf(
    ORIGINAL to EnumSet.of(
      ADIMP, YOI,
      ADIMP_ORA, YOI_ORA
    ),
    FOUR_TO_UNDER_SEVEN to EnumSet.of(
      ADIMP, YOI,
      ADIMP_ORA, YOI_ORA
    ),
    UPDATED to EnumSet.of(
      ADIMP, YOI,
      ADIMP_ORA, YOI_ORA
    ),
    SECTION_250 to EnumSet.of(
      SEC250,
      SEC250_ORA
    ),
  )

  fun getQuestionsForSentences(prisonerId: String): CalculationUserQuestions {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    val sentencesAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId)
    return CalculationUserQuestions(
      sentenceQuestions = sentencesAndOffences.mapNotNull {
        val sentenceCalculationType = SentenceCalculationType.from(it.sentenceCalculationType)
        val overEighteenOnSentenceDate = overEighteenOnSentenceDate(prisonerDetails, it)
        val fourToUnderSeven = fourToUnderSeven(it)
        val sevenYearsOrMore = sevenYearsOrMore(it)

        val sentencedAfterPcsc = sentencedAfterPcsc(it)
        val sentencedWithinOriginalSdsPlusWindow = sentencedWithinOriginalSdsPlusWindow(it)

        var question: CalculationSentenceQuestion? = null
        if (sentencedWithinOriginalSdsPlusWindow) {
          val matchingSentenceType = postPcscCalcTypes[ORIGINAL]!!.contains(sentenceCalculationType)
          if (matchingSentenceType && sevenYearsOrMore && overEighteenOnSentenceDate) {
            question = CalculationSentenceQuestion(it.sentenceSequence, ORIGINAL)
          }
        } else if (sentencedAfterPcsc) {
          if (fourToUnderSeven) {
            val matchingSentenceType = postPcscCalcTypes[FOUR_TO_UNDER_SEVEN]!!.contains(sentenceCalculationType)
            if (matchingSentenceType && overEighteenOnSentenceDate) {
              question = CalculationSentenceQuestion(it.sentenceSequence, FOUR_TO_UNDER_SEVEN)
            }
          } else if (sevenYearsOrMore) {
            val isUpdatedSentenceType = postPcscCalcTypes[UPDATED]!!.contains(sentenceCalculationType)
            val isSection250SentenceType = postPcscCalcTypes[SECTION_250]!!.contains(sentenceCalculationType)
            if (isUpdatedSentenceType && overEighteenOnSentenceDate) {
              question = CalculationSentenceQuestion(it.sentenceSequence, UPDATED)
            } else if (isSection250SentenceType) {
              question = CalculationSentenceQuestion(it.sentenceSequence, SECTION_250)
            }
          }
        }
        question
      }
    )
  }

  private fun sentencedAfterPcsc(sentence: SentenceAndOffences): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(
      PCSC_COMMENCEMENT_DATE
    )
  }

  private fun sentencedWithinOriginalSdsPlusWindow(sentence: SentenceAndOffences): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(SDS_PLUS_COMMENCEMENT_DATE) && !sentencedAfterPcsc(sentence)
  }

  private fun overEighteenOnSentenceDate(prisonerDetails: PrisonerDetails, sentence: SentenceAndOffences): Boolean {
    val ageDuration = Period.between(prisonerDetails.dateOfBirth, sentence.sentenceDate)
    return ageDuration.years >= 18
  }

  private fun sevenYearsOrMore(sentence: SentenceAndOffences): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(sentence: SentenceAndOffences): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }

  private fun endOfSentence(sentence: SentenceAndOffences): LocalDate {
    val duration = Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }
}
