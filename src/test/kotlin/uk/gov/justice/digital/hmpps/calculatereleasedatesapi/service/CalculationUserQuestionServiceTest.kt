package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.LocalDate

class CalculationUserQuestionServiceTest {
  private val featureToggles = FeatureToggles()
  private val calculationUserQuestionService = CalculationUserQuestionService(featureToggles)

  val offences = listOf(
    OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = FIRST_MAY_2020,
      offenceCode = "ABC",
      offenceDescription = "Littering",
      indicators = listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR)
    ),
  )

  private val under7YearSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 1,
    lineSequence = 1,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2020,
    terms = listOf(
      SentenceTerms(years = 6, months = 11)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val sdsPlusSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 2,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2020,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val beforeSdsWindow = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 3,
    lineSequence = 3,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2018,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val afterSdsWindow = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 4,
    lineSequence = 4,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2023,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  val ftrSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 5,
    lineSequence = 5,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2020,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.FTR.name,
    sentenceTypeDescription = "Fixed Term Recall",
    offences = offences,
  )

  val over18PrisonerDetails = PrisonerDetails(
    1,
    "asd",
    dateOfBirth = LocalDate.of(1980, 1, 1),
    firstName = "Harry",
    lastName = "Houdini"
  )

  val under18PrisonerDetails = PrisonerDetails(
    1,
    "asd",
    dateOfBirth = LocalDate.of(2015, 1, 1),
    firstName = "Harry",
    lastName = "Houdini"
  )

  @Test
  fun `The sentences which may fall under SDS+ are returned`() {
    val under18Result = calculationUserQuestionService.getQuestionsForSentences(under18PrisonerDetails, listOf(sdsPlusSentence, beforeSdsWindow, afterSdsWindow, under7YearSentence, ftrSentence))
    assertThat(under18Result.sentenceQuestions).isEmpty()

    val over18Result = calculationUserQuestionService.getQuestionsForSentences(over18PrisonerDetails, listOf(sdsPlusSentence, beforeSdsWindow, afterSdsWindow, under7YearSentence, ftrSentence))
    assertThat(over18Result.sentenceQuestions.size).isEqualTo(1)
    assertThat(over18Result.sentenceQuestions[0].sentenceSequence).isEqualTo(sdsPlusSentence.sentenceSequence)
    assertThat(over18Result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.SCHEDULE_15_ATTRACTING_LIFE)
  }

  private companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2020: LocalDate = LocalDate.of(2020, 5, 1)
    val FIRST_MAY_2023: LocalDate = LocalDate.of(2023, 5, 1)
  }
}
