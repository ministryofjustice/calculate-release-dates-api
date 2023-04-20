package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiDataVersions
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
}
