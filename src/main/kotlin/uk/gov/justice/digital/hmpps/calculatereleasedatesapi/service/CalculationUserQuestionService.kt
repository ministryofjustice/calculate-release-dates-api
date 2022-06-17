package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceQuestion
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserQuestions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.ADIMP_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC250_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC91_03
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.SEC91_03_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.YOI_ORA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.EnumSet

@Service
class CalculationUserQuestionService(
  val featureToggles: FeatureToggles
) {
  val sentenceCalcTypes: EnumSet<SentenceCalculationType> = EnumSet.of(
    ADIMP, YOI, SEC250, SEC91_03,
    ADIMP_ORA, YOI_ORA, SEC250_ORA, SEC91_03_ORA,
  )

  val postPcscCalcTypes: Map<UserInputType, EnumSet<SentenceCalculationType>> = mapOf(
    UserInputType.ORIGINAL to EnumSet.of(
      ADIMP, YOI, SEC250, SEC91_03,
      ADIMP_ORA, YOI_ORA, SEC250_ORA, SEC91_03_ORA,
    ),
    UserInputType.FOUR_TO_UNDER_SEVEN to EnumSet.of(
      ADIMP, YOI,
      ADIMP_ORA, YOI_ORA
    ),
    UserInputType.UPDATED to EnumSet.of(
      ADIMP, YOI,
      ADIMP_ORA, YOI_ORA
    ),
    UserInputType.SECTION_250 to EnumSet.of(
      SEC250,
      SEC250_ORA
    ),
  )

  fun getQuestionsForSentences(prisonerDetails: PrisonerDetails, sentencesAndOffences: List<SentenceAndOffences>): CalculationUserQuestions {
    return if (LocalDate.now().isAfter(featureToggles.pcscStartDate)) {
      postPcscQuestions(prisonerDetails, sentencesAndOffences)
    } else {
      prePcscQuestions(prisonerDetails, sentencesAndOffences)
    }
  }

  fun postPcscQuestions(prisonerDetails: PrisonerDetails, sentencesAndOffences: List<SentenceAndOffences>): CalculationUserQuestions {
    return CalculationUserQuestions(
      sentenceQuestions = sentencesAndOffences.mapNotNull {
        val sentenceCalculationType = SentenceCalculationType.from(it.sentenceCalculationType)
        val duration = Period.of(it.terms[0].years, it.terms[0].months, it.terms[0].weeks * 7 + it.terms[0].days)
        val ageDuration = Period.between(prisonerDetails.dateOfBirth, it.sentenceDate)
        val endOfDuration = it.sentenceDate.plus(duration)
        val endOfSevenYears = it.sentenceDate.plusYears(7)
        val endOfFourYears = it.sentenceDate.plusYears(7)
        val afterPcsc = it.sentenceDate.isAfterOrEqualTo(
          featureToggles.pcscStartDate
        )
        val sevenYearsOrMore = endOfDuration.isAfterOrEqualTo(endOfSevenYears)
        val betweenFourAndSevenYears = endOfDuration.isAfterOrEqualTo(endOfFourYears) && endOfDuration.isBefore(endOfSevenYears)
        val overEighteen = ageDuration.years >= 18
        val withinOriginalWindow =
          it.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && !afterPcsc

        if (withinOriginalWindow) {
          val matchingSentenceType = postPcscCalcTypes[UserInputType.ORIGINAL]!!.contains(sentenceCalculationType)
          if (matchingSentenceType && sevenYearsOrMore && overEighteen) {
            CalculationSentenceQuestion(it.sentenceSequence, UserInputType.ORIGINAL)
          }
        } else {
          if (betweenFourAndSevenYears) {
            val matchingSentenceType = postPcscCalcTypes[UserInputType.FOUR_TO_UNDER_SEVEN]!!.contains(sentenceCalculationType)
            if (matchingSentenceType && overEighteen) {
              CalculationSentenceQuestion(it.sentenceSequence, UserInputType.FOUR_TO_UNDER_SEVEN)
            }
          } else {
            val isUpdatedSentenceType = postPcscCalcTypes[UserInputType.UPDATED]!!.contains(sentenceCalculationType)
            val isSection250SentenceType = postPcscCalcTypes[UserInputType.SECTION_250]!!.contains(sentenceCalculationType)
            if (isUpdatedSentenceType && sevenYearsOrMore && overEighteen) {
              CalculationSentenceQuestion(it.sentenceSequence, UserInputType.UPDATED)
            } else if (isSection250SentenceType && sevenYearsOrMore) {
              CalculationSentenceQuestion(it.sentenceSequence, UserInputType.SECTION_250)
            }
          }
        }
        null
      }
    )
  }

  fun prePcscQuestions(prisonerDetails: PrisonerDetails, sentencesAndOffences: List<SentenceAndOffences>): CalculationUserQuestions {
    return CalculationUserQuestions(
      sentenceQuestions = sentencesAndOffences.mapNotNull {
        if (!sentenceCalcTypes.contains(SentenceCalculationType.from(it.sentenceCalculationType))) {
          null
        } else {
          val duration = Period.of(it.terms[0].years, it.terms[0].months, it.terms[0].weeks * 7 + it.terms[0].days)
          val ageDuration = Period.between(prisonerDetails.dateOfBirth, it.sentenceDate)
          val endOfDuration = it.sentenceDate.plus(duration)
          val endOfSevenYears = it.sentenceDate.plusYears(7)

          val sevenYearsOrMore = endOfDuration.isAfterOrEqualTo(endOfSevenYears)
          val overEighteen = ageDuration.years >= 18
          val withinSdsPlusWindow =
            it.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && it.sentenceDate.isBefore(
              featureToggles.pcscStartDate
            )

          if (sevenYearsOrMore && overEighteen && withinSdsPlusWindow) {
            CalculationSentenceQuestion(it.sentenceSequence, UserInputType.SCHEDULE_15_ATTRACTING_LIFE)
          } else {
            null
          }
        }
      }
    )
  }
}
