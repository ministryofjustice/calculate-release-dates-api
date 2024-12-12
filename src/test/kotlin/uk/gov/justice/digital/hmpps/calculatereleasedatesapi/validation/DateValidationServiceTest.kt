package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class DateValidationServiceTest {
  private val dateValidationService = DateValidationService()

  @Test
  fun `validateDates should return missing date error`() {
    val dates = listOf("CRD", "SED")
    val result = dateValidationService.validateDates(dates)
    assertEquals("You cannot select a CRD and a SED without a LED", result.first().message)
  }

  @Test
  fun `validateDates should return incompatible dates error`() {
    dateValidationService.getIncompatibleDatePairs().forEach { (first, second) ->
      val result = dateValidationService.validateDates(listOf(first, second))
      assertEquals("$first and $second cannot be selected together", result.first().message)
    }
  }

  @Test
  fun `validateDates should return multiple errors`() {
    val dates = listOf("CRD", "ARD", "HDCAD", "PRRD", "SED")
    val result = dateValidationService.validateDates(dates)
    assertEquals(3, result.size)
    assertEquals("You cannot select a CRD and a SED without a LED", result[0].message)
    assertEquals("CRD and ARD cannot be selected together", result[1].message)
    assertEquals("HDCAD and PRRD cannot be selected together", result[2].message)
  }

  @Test
  fun `validateDates should return no errors for valid dates`() {
    val dates = listOf("CRD", "HDCED", "LED", "TUSED")
    val result = dateValidationService.validateDates(dates)
    assertEquals(0, result.size)
  }
}
