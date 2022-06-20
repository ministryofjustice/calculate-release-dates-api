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

class CalculationUserQuestionServicePostPcscTest {
  private val pcscDate = LocalDate.now().minusDays(1)
  private val featureToggles = FeatureToggles(pcscStartDate = pcscDate)
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

  private val under7YearSentencePrePcsc = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 1,
    lineSequence = 1,
    caseSequence = 1,
    sentenceDate = pcscDate.minusDays(1),
    terms = listOf(
      SentenceTerms(years = 6, months = 11)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val under4YearSentencePostPcsc = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 2,
    lineSequence = 1,
    caseSequence = 1,
    sentenceDate = pcscDate.plusDays(1),
    terms = listOf(
      SentenceTerms(years = 3, months = 11)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val originalSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 3,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = pcscDate.minusDays(1),
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val fourToUnderSevenSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 4,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = pcscDate,
    terms = listOf(
      SentenceTerms(years = 6)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val updatedSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 5,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = pcscDate,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = offences,
  )

  private val section250Sentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 6,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = pcscDate,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.SEC250.name,
    sentenceTypeDescription = "SEC250",
    offences = offences,
  )

  private val edsSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 7,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = FIRST_MAY_2020,
    terms = listOf(
      SentenceTerms(years = 8)
    ),
    sentenceStatus = "EDS",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.EDS21.name,
    sentenceTypeDescription = "EDS21",
    offences = offences,
  )

  private val beforeSdsWindow = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 8,
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

  val ftrSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 9,
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
  fun `The sentences which may fall under PCSC, but for an under 18 year old`() {
    val result = calculationUserQuestionService.getQuestionsForSentences(under18PrisonerDetails, listOf(under7YearSentencePrePcsc,
      beforeSdsWindow, under4YearSentencePostPcsc, originalSentence, fourToUnderSevenSentence, updatedSentence, section250Sentence,
      edsSentence, beforeSdsWindow, ftrSentence))
    assertThat(result.sentenceQuestions.size).isEqualTo(1)
    assertThat(result.sentenceQuestions[0].sentenceSequence).isEqualTo(section250Sentence.sentenceSequence)
    assertThat(result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.SECTION_250)
  }

  @Test
  fun `The service identifies original sds+ sentences`() {
    val result = calculationUserQuestionService.getQuestionsForSentences(over18PrisonerDetails, listOf(originalSentence, edsSentence, beforeSdsWindow, ftrSentence, under7YearSentencePrePcsc,
      beforeSdsWindow))
    assertThat(result.sentenceQuestions.size).isEqualTo(1)
    assertThat(result.sentenceQuestions[0].sentenceSequence).isEqualTo(originalSentence.sentenceSequence)
    assertThat(result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.ORIGINAL)
  }

  @Test
  fun `The service identifies original four to under seven sentences`() {
    val result = calculationUserQuestionService.getQuestionsForSentences(over18PrisonerDetails, listOf(fourToUnderSevenSentence))
    assertThat(result.sentenceQuestions.size).isEqualTo(1)
    assertThat(result.sentenceQuestions[0].sentenceSequence).isEqualTo(fourToUnderSevenSentence.sentenceSequence)
    assertThat(result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.FOUR_TO_UNDER_SEVEN)
  }

  @Test
  fun `The service identifies updated  sentences`() {
    val result = calculationUserQuestionService.getQuestionsForSentences(over18PrisonerDetails, listOf(updatedSentence))
    assertThat(result.sentenceQuestions.size).isEqualTo(1)
    assertThat(result.sentenceQuestions[0].sentenceSequence).isEqualTo(updatedSentence.sentenceSequence)
    assertThat(result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.UPDATED)
  }

  @Test
  fun `The service identifies sec250  sentences`() {
    val result = calculationUserQuestionService.getQuestionsForSentences(over18PrisonerDetails, listOf(section250Sentence))
    assertThat(result.sentenceQuestions.size).isEqualTo(1)
    assertThat(result.sentenceQuestions[0].sentenceSequence).isEqualTo(section250Sentence.sentenceSequence)
    assertThat(result.sentenceQuestions[0].userInputType).isEqualTo(UserInputType.SECTION_250)
  }

  private companion object {
    val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    val FIRST_MAY_2020: LocalDate = LocalDate.of(2020, 5, 1)
  }
}
