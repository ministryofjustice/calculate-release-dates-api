package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OffenderTest {

  @Test
  fun testConstructor() {
    val name = "Joel"
    val reference = "A1234BC"
    val offender = Offender(reference, name)

    assertEquals(name, offender.name)
    assertEquals(reference, offender.reference)
  }
}
