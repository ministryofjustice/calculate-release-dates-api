package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

internal class OffenceTest {
  @Test
  fun testOffence() {
    val startedAt = LocalDate.of(2020, 1, 1)
    val offence = Offence(startedAt, Optional.empty())
    assertEquals(offence.startedAt, startedAt)
    assertTrue(offence.endedAt.isEmpty)
  }
}
