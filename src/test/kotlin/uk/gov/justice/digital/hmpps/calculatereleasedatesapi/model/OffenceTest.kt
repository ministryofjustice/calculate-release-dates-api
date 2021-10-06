package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OffenceTest {
  @Test
  fun testOffence() {
    val committedAt = LocalDate.of(2020, 1, 1)
    val offence = Offence(committedAt)
    assertEquals(offence.committedAt, committedAt)
  }

  @Test
  fun testOffenceOneArgument() {
    val committedAt = LocalDate.of(2020, 1, 1)
    val offence = Offence(committedAt)
    assertEquals(offence.committedAt, committedAt)
  }
}
