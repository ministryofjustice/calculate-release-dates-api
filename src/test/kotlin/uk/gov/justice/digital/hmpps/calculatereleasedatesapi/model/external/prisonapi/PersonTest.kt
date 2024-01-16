package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.Alert
import java.time.LocalDate

internal class PersonTest {

  @Test
  fun `test has active sex offender alert`() {
    val sexOffenderAlert = Alert(LocalDate.now(), alertType = "S", alertCode = "SOR")
    val person = Person("ABC1234", LocalDate.now().minusYears(23), "Davis", listOf(sexOffenderAlert))

    assertTrue(person.isActiveSexOffender())
  }

  @Test
  fun `test has expired sex offender alert`() {
    val expiredSexOffenderAlert = Alert(LocalDate.now(), dateExpires = LocalDate.now().minusDays(1), alertType = "S", alertCode = "SOR")
    val person = Person("ABC1234", LocalDate.now().minusYears(23), "Collins", listOf(expiredSexOffenderAlert))

    assertFalse(person.isActiveSexOffender())
  }

  @Test
  fun `test does not have sex offender alert`() {
    val alert = Alert(LocalDate.now(), dateExpires = LocalDate.now().minusDays(1), alertType = "A", alertCode = "DEF")
    val person = Person("ABC1234", LocalDate.now().minusYears(23), "Zinis", listOf(alert))

    assertFalse(person.isActiveSexOffender())
  }
}
