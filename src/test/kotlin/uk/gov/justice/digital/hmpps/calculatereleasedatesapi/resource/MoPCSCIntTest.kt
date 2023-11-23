package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OffenceSdsPlusLookupService
import java.time.LocalDate

class MoPCSCIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var offenceSdsPlusLookupService: OffenceSdsPlusLookupService

  @Test
  fun `Test Call to MO Service Matching S250 Offence marked as SDS+`() {
    //S250 over 7 years and sentenced after PCSC date
    val inputOffenceList = listOf(
      SentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2022, 1, 1),
            null,
            "COML025A",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    UserContext.setAuthToken("123456")
    offenceSdsPlusLookupService.setSdsPlusMarkerForOffences(inputOffenceList)
    assertTrue(inputOffenceList[0].offences[0].isPcscSdsPlus)
  }

  @Test
  fun `Test Call to MO Service Matching S250 Offence marked as SDS+ `() {
    //S250 over 7 years and sentenced after PCSC date
    val inputOffenceList = listOf(
      SentenceAndOffences(
        1,
        1,
        1,
        1,
        null,
        "TEST",
        "TEST",
        SentenceCalculationType.SEC250.toString(),
        "TEST",
        LocalDate.of(2022, 8, 29),
        listOf(SentenceTerms(8, 4, 1, 1)),
        listOf(
          OffenderOffence(
            1,
            LocalDate.of(2022, 1, 1),
            null,
            "COML025A",
            "TEST OFFENSE",
          ),
          OffenderOffence(
            1,
            LocalDate.of(2022, 1, 1),
            null,
            "COML022",
            "TEST OFFENSE",
          ),
        ),
      ),
    )

    UserContext.setAuthToken("123456")
    offenceSdsPlusLookupService.setSdsPlusMarkerForOffences(inputOffenceList)
    assertTrue(inputOffenceList[0].offences[0].isPcscSdsPlus)
    assertTrue(inputOffenceList[0].offences[0].isPcscSdsPlus)
  }
}