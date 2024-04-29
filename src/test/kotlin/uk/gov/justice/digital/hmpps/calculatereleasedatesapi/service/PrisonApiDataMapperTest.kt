package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import java.time.LocalDate

class PrisonApiDataMapperTest {

  private val objectMapper = TestUtil.objectMapper()
  private val prisonApiDataMapper = PrisonApiDataMapper(objectMapper)

  @Test
  fun `Map version 0 of sentences and offences`() {
    val version0 = PrisonApiDataVersions.Version0.SentenceAndOffences(
      bookingId = 1,
      sentenceSequence = 1,
      consecutiveToSequence = 1,
      sentenceDate = LocalDate.of(2022, 2, 1),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = "SDS",
      sentenceTypeDescription = "Standard Determinate",
      lineSequence = 1,
      caseSequence = 2,
      days = 1,
      weeks = 2,
      months = 3,
      years = 4,
      offences = listOf(
        OffenderOffence(
          offenderChargeId = 1L,
          offenceStartDate = null,
          offenceEndDate = null,
          offenceCode = "Dummy Offence",
          offenceDescription = "A Dummy description",
        ),
      ),
    )

    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version0)),
      sentenceAndOffencesVersion = 0,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)

    assertThat(sentencesAndOffences).isNotNull
    assertThat(sentencesAndOffences[0].terms).isNotEmpty
    assertThat(sentencesAndOffences[0].terms.size).isEqualTo(1)
    assertThat(sentencesAndOffences[0].terms[0].days).isEqualTo(1)
    assertThat(sentencesAndOffences[0].terms[0].weeks).isEqualTo(2)
    assertThat(sentencesAndOffences[0].terms[0].months).isEqualTo(3)
    assertThat(sentencesAndOffences[0].terms[0].years).isEqualTo(4)
  }

  @Test
  fun `Map version 1 of sentences and offences`() {
    val version1 = PrisonApiSentenceAndOffences(
      bookingId = 1L,
      sentenceSequence = 3,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
      terms = listOf(
        SentenceTerms(years = 8),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      offences = listOf(OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A"))),
    )
    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version1)),
      sentenceAndOffencesVersion = 1,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(listOf(SentenceAndOffenceWithReleaseArrangements(version1, version1.offences[0], false)))
  }

  @Test
  fun `Map version 2 of sentences and offences`() {
    val version2 = SentenceAndOffenceWithReleaseArrangements(
      bookingId = 1L,
      sentenceSequence = 3,
      consecutiveToSequence = null,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
      terms = listOf(
        SentenceTerms(years = 8),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A")),
      caseReference = null,
      courtDescription = null,
      fineAmount = null,
      isSDSPlus = true,
    )
    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version2)),
      sentenceAndOffencesVersion = 2,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(listOf(version2))
  }
}
