package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OffenderTest {

  @Test
  fun testConstructor() {
    val name = "AN.Other"
    val reference = "A1234BC"
    val dateOfBirth = LocalDate.of(1970, 3, 3)
    val offender = Offender(reference, name, dateOfBirth)

    assertEquals(name, offender.name)
    assertEquals(reference, offender.reference)
  }
}
