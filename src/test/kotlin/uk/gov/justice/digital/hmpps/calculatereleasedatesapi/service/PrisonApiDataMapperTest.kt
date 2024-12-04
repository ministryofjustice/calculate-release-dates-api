package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffencesWithSDSPlus
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
        OffenderOffence(
          offenderChargeId = 2L,
          offenceStartDate = null,
          offenceEndDate = null,
          offenceCode = "Another Dummy Offence",
          offenceDescription = "Another Dummy description",
        ),
      ),
    )

    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version0)),
      sentenceAndOffencesVersion = 0,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)

    assertThat(sentencesAndOffences).isNotNull
    assertThat(sentencesAndOffences).hasSize(2)
    assertThat(sentencesAndOffences[0].terms).isNotEmpty
    assertThat(sentencesAndOffences[0].terms.size).isEqualTo(1)
    assertThat(sentencesAndOffences[0].terms[0].days).isEqualTo(1)
    assertThat(sentencesAndOffences[0].terms[0].weeks).isEqualTo(2)
    assertThat(sentencesAndOffences[0].terms[0].months).isEqualTo(3)
    assertThat(sentencesAndOffences[0].terms[0].years).isEqualTo(4)
    assertThat(sentencesAndOffences[0].offence.offenceCode).isEqualTo("Dummy Offence")
    assertThat(sentencesAndOffences[1].offence.offenceCode).isEqualTo("Another Dummy Offence")
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
      offences = listOf(
        OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "Dummy Offence", "description", listOf("A")),
        OffenderOffence(2L, LocalDate.of(2015, 1, 1), null, "Another Dummy Offence", "description", listOf("A")),
      ),
    )
    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version1)),
      sentenceAndOffencesVersion = 1,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(
      listOf(
        SentenceAndOffenceWithReleaseArrangements(
          source = version1,
          offence = version1.offences[0],
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        ),
        SentenceAndOffenceWithReleaseArrangements(
          source = version1,
          offence = version1.offences[1],
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        ),
      ),
    )
  }

  @Test
  fun `Map version 2 of sentences and offences`() {
    val version2 = SentenceAndOffencesWithSDSPlus(
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
      offences = listOf(
        OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "Dummy Offence", "description", listOf("A")),
        OffenderOffence(2L, LocalDate.of(2015, 1, 1), null, "Another Dummy Offence", "description", listOf("A")),
      ),
      caseReference = null,
      courtDescription = null,
      fineAmount = null,
      isSDSPlus = true,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
    )
    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version2)),
      sentenceAndOffencesVersion = 2,
    )

    val aNewSentenceAndOffence = SentenceAndOffenceWithReleaseArrangements(
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
      offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "Dummy Offence", "description", listOf("A")),
      caseReference = null,
      courtDescription = null,
      fineAmount = null,
      isSDSPlus = true,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val theOtherSentenceAndOffence = aNewSentenceAndOffence.copy(offence = OffenderOffence(2L, LocalDate.of(2015, 1, 1), null, "Another Dummy Offence", "description", listOf("A")))

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(listOf(aNewSentenceAndOffence, theOtherSentenceAndOffence))
  }

  @Test
  fun `Map version 3 of sentences and offences`() {
    val version3 = SentenceAndOffenceWithReleaseArrangements(
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.SEXUAL,
    )
    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.valueToTree(listOf(version3)),
      sentenceAndOffencesVersion = 3,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(listOf(version3))
  }

  @Test
  fun `Map version 3 of sentences and offences missing SDS exclusion type defaults to NO`() {
    val expected = SentenceAndOffenceWithReleaseArrangements(
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
      isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
      isSDSPlusOffenceInPeriod = true,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val jsonWithoutHasAnSDSExclusion = "[{\"bookingId\":1,\"sentenceSequence\":3,\"lineSequence\":2,\"caseSequence\":1,\"consecutiveToSequence\":null," +
      "\"sentenceStatus\":\"IMP\",\"sentenceCategory\":\"CAT\",\"sentenceCalculationType\":\"ADIMP\",\"sentenceTypeDescription\":\"ADMIP\"," +
      "\"sentenceDate\":\"2022-06-27\",\"terms\":[{\"years\":8,\"months\":0,\"weeks\":0,\"days\":0,\"code\":\"IMP\"}],\"offence\":" +
      "{\"offenderChargeId\":1,\"offenceStartDate\":\"2015-01-01\",\"offenceEndDate\":null,\"offenceCode\":\"ADIMP_ORA\"," +
      "\"offenceDescription\":\"description\",\"indicators\":[\"A\"]},\"caseReference\":null,\"courtDescription\":null,\"fineAmount\":null" +
      ",\"isSDSPlus\":true,\"isSDSPlusEligibleSentenceTypeLengthAndOffence\":true,\"isSDSPlusOffenceInPeriod\":true}]"

    val calculationRequest = CalculationRequest(
      sentenceAndOffences = objectMapper.readTree(jsonWithoutHasAnSDSExclusion),
      sentenceAndOffencesVersion = 3,
    )

    val sentencesAndOffences = prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)
    assertThat(sentencesAndOffences).isEqualTo(listOf(expected))
  }
}
