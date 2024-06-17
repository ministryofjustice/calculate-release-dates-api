package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NomisTusedDataTest {

  @Test
  fun getLatestTusedDate() {
    val nomisTusedData = NomisTusedData(LocalDate.of(2024, 5, 4), null, null, "A12345AB")

    assertEquals(LocalDate.of(2024, 5, 4), nomisTusedData.getLatestTusedDate())

    nomisTusedData.latestOverrideTused = LocalDate.of(2023, 6, 9)

    assertEquals(LocalDate.of(2023, 6, 9), nomisTusedData.getLatestTusedDate())
  }
}
