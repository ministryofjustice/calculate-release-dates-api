package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.WorkingDay
import java.time.LocalDate

class WorkingDayIntTest : IntegrationTestBase() {

  @Test
  fun `nextWorkingDay test weekend adjustment`() {
    val result = makeApiCall("/working-day/next/$SATURDAY")

    assertEquals(MONDAY, result.date)
    assertTrue(result.adjustedForWeekend)
    assertFalse(result.adjustedForBankHoliday)
  }

  @Test
  fun `previousWorkingDay test weekend adjustment`() {
    val result = makeApiCall("/working-day/previous/$SATURDAY")

    assertEquals(FRIDAY, result.date)
    assertTrue(result.adjustedForWeekend)
    assertFalse(result.adjustedForBankHoliday)
  }

  private fun makeApiCall(uri: String) = webTestClient.get()
    .uri(uri)
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(WorkingDay::class.java)
    .returnResult().responseBody!!

  companion object {
    val SATURDAY: LocalDate = LocalDate.of(2021, 10, 23)
    val MONDAY: LocalDate = LocalDate.of(2021, 10, 25)
    val FRIDAY: LocalDate = LocalDate.of(2021, 10, 22)
  }
}
