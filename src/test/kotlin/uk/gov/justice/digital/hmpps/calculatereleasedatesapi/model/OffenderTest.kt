package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OffenderTest {

  @Test
  fun testConstructor() {
    val reference = "A1234BC"
    val dateOfBirth = LocalDate.of(1970, 3, 3)
    val offender = Offender(reference, dateOfBirth)

    assertEquals(reference, offender.reference)
  }
}
