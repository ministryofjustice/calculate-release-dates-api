package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.calculation

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.util.stream.Stream

@ActiveProfiles(Sds50Tests.Companion.TEST_CONFIGURATION)
class Sds50Tests : CalculationExampleTests() {

  @ParameterizedTest
  @MethodSource(value = ["testCases"])
  fun `Test calculation with sds-50 configuration`(
    example: String,
  ) {
    `Test Example`(example)
  }

  @ParameterizedTest
  @MethodSource(value = ["breakdownTestCaseSource"])
  fun `Test breakdown with sds-50 configuration`(
    example: String,
  ) {
    `Test UX Example Breakdowns`(example)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TEST_CONFIGURATION = "sds-50"

    @JvmStatic
    fun testCases(): Stream<Arguments> {
      val excluded = listOf("custom-examples/different-calclulation-from-stored")
      val dir = File(object {}.javaClass.getResource("/test_data/overall_calculation").file)
      return getTestCasesFromDir(dir, excluded, TEST_CONFIGURATION)
    }

    @JvmStatic
    fun breakdownTestCaseSource(): Stream<Arguments> {
      return emptyList<Arguments>().stream()
//      val dir = File(object {}.javaClass.getResource("/test_data/calculation_breakdown_response").file)
//      return getTestCasesFromDir(dir, listOf(), TEST_CONFIGURATION)
    }
  }
}
