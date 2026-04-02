package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.calculation

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.util.stream.Stream

@ActiveProfiles(ProgressionModelCalculationTests.TEST_CONFIGURATION)
class ProgressionModelCalculationTests : CalculationExampleTests() {

  @ParameterizedTest
  @MethodSource(value = ["testCases"])
  fun `Test calculation with sds-progression-model configuration`(
    example: String,
  ) {
    `Test Example`(example)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TEST_CONFIGURATION = "sds-progression-model"

    @JvmStatic
    fun testCases(): Stream<Arguments> {
      val dir = File(object {}.javaClass.getResource("/test_data/overall_calculation")!!.file)
      return getTestCasesFromDir(dir, listOf(), TEST_CONFIGURATION)
    }
  }
}
