package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdced4ConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

@ExtendWith(MockitoExtension::class)
class SentenceIdentificationServiceTest {
  private val workingDayService = mock<WorkingDayService>()
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdced4Configuration = hdced4ConfigurationForTests()
  private val hdced4Calculator = Hdced4Calculator(hdced4Configuration)
  private val sentenceIdentificationService: SentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdced4Calculator)
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
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[ARD, SED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 12 months  < 4 years

  @Test
  fun `Identify before 2005 12-48M`() {
    val sentence = jsonTransformation.loadSentence("two_years_2003_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[LED, CRD, SED, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 12 months  < 4 years
  // sex offender

  @Test
  fun `Identify before 2005 12-48M but is a sex offender`() {
    val sentence = jsonTransformation.loadSentence("two_years_2003_mar")
    sentenceIdentificationService.identify(sentence, sexOffender)
    assertEquals("[LED, CRD, SED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 4 years
  //  schedule 15 offence: true

  @Test
  fun `Identify before 2005 48M- Section 15`() {
    val sentence = jsonTransformation.loadSentence("five_years_2003_mar_section15")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[PED, NPD, LED, SED, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced before: 03/12/2012
  //   offence before: 04/04/2005
  //  sentence length: > 4 years
  //  schedule 15 offence: false

  @Test
  fun `Identify before 2005 48M-`() {
    val sentence = jsonTransformation.loadSentence("five_years_2003_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[CRD, SLED, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  //   offence after: 04/04/2005
  //  sentence length: < 12 months

  @Test
  fun `Identify Before 2015 (After 2005 and 2012) LT 12 Months`() {
    val sentence = jsonTransformation.loadSentence("two_week_2014_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[ARD, SED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  //   offence after: 04/04/2005
  //  sentence length: > 2 years

  @Test
  fun `Identify Before 2015 (After 2005 and 2012) GT 2 Years`() {
    val sentence = jsonTransformation.loadSentence("three_years_2014_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: < 12 months

  @Test
  fun `Identify After 2015 LT 12 Months`() {
    val sentence = jsonTransformation.loadSentence("two_week_2018_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, TUSED, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 12 - 24 months

  @Test
  fun `Identify After 2015 18 Months`() {
    val sentence = jsonTransformation.loadSentence("eighteen_months_2018_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, TUSED, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 24- months

  @Test
  fun `Identify After 2005 18 Months`() {
    val sentence = jsonTransformation.loadSentence("eighteen_months_2006_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 1 day

  @Test
  fun `Identify After 2015 1 day`() {
    val sentence = jsonTransformation.loadSentence("one_day_2018_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 1 day
  // offender age at 1/2 served: <= 18

  @Test
  fun `Identify After 2015 under 18`() {
    val sentence = jsonTransformation.loadSentence("two_week_2018_mar")
    sentenceIdentificationService.identify(sentence, offenderU18)
    assertEquals("[SLED, CRD, HDCED]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }

  // sentenced after: 03/12/2012
  // offence after: 01/02/2015
  // sentence length: 5 years

  @Test
  fun `Identify After 2015 5 years`() {
    val sentence = jsonTransformation.loadSentence("five_years_2018_mar")
    sentenceIdentificationService.identify(sentence, offender)
    assertEquals("[SLED, CRD, HDCED, HDCED4PLUS]", sentence.releaseDateTypes.getReleaseDateTypes().toString())
  }
}
