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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
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

  fun getQuestionsForSentences(prisonerDetails: PrisonerDetails, sentencesAndOffences: List<SentenceAndOffences>): CalculationUserQuestions {

    return CalculationUserQuestions(
      sentenceQuestions = sentencesAndOffences.mapNotNull {
        if (!sentenceCalcTypes.contains(SentenceCalculationType.from(it.sentenceCalculationType))) {
          null
        } else {
          val duration = Period.of(it.terms[0].years, it.terms[0].months, it.terms[0].weeks * 7 + it.terms[0].days)
          val ageDuration = Period.between(prisonerDetails.dateOfBirth, it.sentenceDate)

          val sevenYearsOrMore = duration.years >= 7
          val overEighteen = ageDuration.years >= 18
          val withinSdsPlusWindow =
            it.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && it.sentenceDate.isBeforeOrEqualTo(
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
