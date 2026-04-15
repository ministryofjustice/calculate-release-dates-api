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
    // `Test Example`("ers0/crs-2642-ac1-1")
    // `Test Example`("ftr-56/crs-2467-ac2-2")
    // `Test Example`("ftr-56/blah") // should be T2 not T3
    // `Test Example`("tranches/crs-2121_5x1")
    // `Test Example`("ftr-56/crs-2462-ac1-2")
    // `Test Example`("ftr-56/crs-2556-ac1-2")
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

  @ParameterizedTest
  @MethodSource(value = ["sentenceLevelDatesTestCaseSource"])
  fun `Test sentence level dates with standard configuration`(
    example: String,
  ) {
    `Test Sentence Level Dates Example`(example)
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

    @JvmStatic
    fun sentenceLevelDatesTestCaseSource(): Stream<Arguments> {
      val dir = File(object {}.javaClass.getResource("/test_data/sentence_level_calculation_response").file)
      return getTestCasesFromDir(dir, listOf(), TEST_CONFIGURATION)
    }
  }
}
