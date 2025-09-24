package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.calculation

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.stream.Stream

class StandardConfigurationTests : CalculationExampleTests() {

  @Test
  fun blah() {
    `Test calculation with standard configuration`("/custom-examples/crs-2343")
  }

  @ParameterizedTest
  @MethodSource(value = ["testCases"])
  fun `Test calculation with standard configuration`(
    example: String,
  ) {
    `Test Example`(example)
  }

  @ParameterizedTest
  @MethodSource(value = ["breakdownTestCaseSource"])
  fun `Test breakdown with standard configuration`(
    example: String,
  ) {
    `Test UX Example Breakdowns`(example)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TEST_CONFIGURATION = "calculation-params"

    @JvmStatic
    fun testCases(): Stream<Arguments> {
      val dir = File(object {}.javaClass.getResource("/test_data/overall_calculation").file)
      return getTestCasesFromDir(dir, listOf(), TEST_CONFIGURATION)
    }

    @JvmStatic
    fun breakdownTestCaseSource(): Stream<Arguments> {
      val dir = File(object {}.javaClass.getResource("/test_data/calculation_breakdown_response").file)
      return getTestCasesFromDir(dir, listOf(), TEST_CONFIGURATION)
    }
  }
}
