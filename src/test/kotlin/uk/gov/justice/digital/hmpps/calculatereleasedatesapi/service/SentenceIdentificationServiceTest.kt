package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS

@ExtendWith(MockitoExtension::class)
class SentenceIdentificationServiceTest {
  private val tusedCalculator = TusedCalculator(FeatureToggles(validatePostRecallRepealDate = false))
  private val hdcedCalculator = HdcedCalculator(hdcedConfigurationForTests())
  private val sentenceIdentificationService: SentenceIdentificationService =
    SentenceIdentificationService(tusedCalculator, hdcedCalculator)
  private val jsonTransformation = JsonTransformation()
  private val offender = jsonTransformation.loadOffender("john_doe")
  private val offenderU18 = jsonTransformation.loadOffender("john_doe_under18")
  private val sexOffender = jsonTransformation.loadOffender("john_doe_sex_offender")

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: < 12 months

  @Test
  fun `Identify before 2005 -12M`() {
    val sentence = jsonTransformation.loadSentence("two_week_2003_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[ARD, SED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 12 months  < 4 years

  @Test
  fun `Identify before 2005 12-48M`() {
    val sentence = jsonTransformation.loadSentence("two_years_2003_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[LED, CRD, SED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 12 months  < 4 years
  // sex offender

  @Test
  fun `Identify before 2005 12-48M but is a sex offender`() {
    val sentence = jsonTransformation.loadSentence("two_years_2003_mar")
    sentenceIdentificationService.identify(
      sentence,
      sexOffender,
    )
    assertEquals("[LED, CRD, SED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 4 years
  //  schedule 15 offence: false

  @Test
  fun `Identify before 2005 48M-`() {
    val sentence = jsonTransformation.loadSentence("five_years_2003_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[CRD, SLED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  //   offence after: 04/04/2005
  //  sentence length: < 12 months

  @Test
  fun `Identify Before 2015 (After 2005 and 2012) LT 12 Months`() {
    val sentence = jsonTransformation.loadSentence("two_week_2014_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SED, ARD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  //   offence after: 04/04/2005
  //  sentence length: > 2 years

  @Test
  fun `Identify Before 2015 (After 2005 and 2012) GT 2 Years`() {
    val sentence = jsonTransformation.loadSentence("three_years_2014_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: < 12 months

  @Test
  fun `Identify After 2015 LT 12 Months`() {
    val sentence = jsonTransformation.loadSentence("two_week_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, TUSED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 12 - 24 months

  @Test
  fun `Identify After 2015 18 Months`() {
    val sentence = jsonTransformation.loadSentence("eighteen_months_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, TUSED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 24- months

  @Test
  fun `Identify After 2005 18 Months`() {
    val sentence = jsonTransformation.loadSentence("eighteen_months_2006_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 1 day

  @Test
  fun `Identify After 2015 1 day`() {
    val sentence = jsonTransformation.loadSentence("one_day_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SED, ARD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  @Test
  fun `Identify After 2015 2 day`() {
    val sentence = jsonTransformation.loadSentence("two_day_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, TUSED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 1 day
  // offender age at 1/2 served: <= 18

  @Test
  fun `Identify After 2015 under 18`() {
    val sentence = jsonTransformation.loadSentence("two_week_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offenderU18,
    )
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 5 years

  @Test
  fun `Identify After 2015 5 years`() {
    val sentence = jsonTransformation.loadSentence("five_years_2018_mar")
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 5 years

  @ParameterizedTest
  @CsvSource(
    "true,false,NO,SDS",
    "false,false,NO,SDS",
    "true,false,SEXUAL,SDS",
    "false,false,SEXUAL,SDS",
    "true,false,VIOLENT,SDS",
    "false,false,VIOLENT,SDS",
    "false,true,NO,SDS_PLUS",
    "false,true,SEXUAL,SDS_PLUS",
    "false,true,VIOLENT,SDS_PLUS",
  )
  fun `Identify SDS early release correctly`(
    beforeCjaLaspo: Boolean,
    sdsPlus: Boolean,
    exclusion: SDSEarlyReleaseExclusionType,
    expected: SentenceIdentificationTrack,
  ) {
    val (offenceDate, sentenceDate) = if (beforeCjaLaspo) {
      LocalDate.of(2005, 1, 1) to LocalDate.of(2005, 1, 1)
    } else {
      LocalDate.of(2016, 1, 1) to LocalDate.of(2016, 1, 1)
    }
    val sentence = StandardDeterminateSentence(
      Offence(offenceDate),
      Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      sentenceDate,
      isSDSPlus = sdsPlus,
      hasAnSDSEarlyReleaseExclusion = exclusion,
    )
    sentenceIdentificationService.identify(
      sentence,
      offender,
    )
    assertThat(sentence.identificationTrack).isEqualTo(expected)
  }
}
