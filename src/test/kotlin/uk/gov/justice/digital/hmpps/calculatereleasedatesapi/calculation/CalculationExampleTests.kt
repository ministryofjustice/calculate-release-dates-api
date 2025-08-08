package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.calculation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.provider.Arguments
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil.Companion.overrideFeatureTogglesForTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SpringTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest.Companion.CALCULATION_REASON
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.fail

@ActiveProfiles("calculation-params")
abstract class CalculationExampleTests : SpringTestBase() {
  protected val jsonTransformation = JsonTransformation()

  @Autowired
  private lateinit var calculationService: CalculationService

  @Autowired
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @Autowired
  private lateinit var validationService: ValidationService

  @Autowired
  private lateinit var featureToggles: FeatureToggles

  protected fun `Test Example`(
    example: String,
  ) {
    log.info("Testing example $example")
    val calculationTestFile = jsonTransformation.loadCalculationTestFile("overall_calculation/$example")
    overrideFeatureTogglesForTest(calculationTestFile, featureToggles)
    val calculatedReleaseDates: CalculationOutput
    val returnedValidationMessages: List<ValidationMessage>
    try {
      calculatedReleaseDates = calculationService
        .calculateReleaseDates(calculationTestFile.booking, calculationTestFile.userInputs)

      returnedValidationMessages = validationService.validateBookingAfterCalculation(
        calculatedReleaseDates,
        calculationTestFile.booking,
      ).distinct()
    } catch (e: Exception) {
      if (!calculationTestFile.error.isNullOrEmpty()) {
        assertEquals(calculationTestFile.error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    log.info(
      "Example $example outcome BookingCalculation: {}",
      TestUtil.Companion.objectMapper().writeValueAsString(calculatedReleaseDates),
    )
    if (calculationTestFile.expectedValidationException != null) {
      val expectedExceptions = calculationTestFile.expectedValidationException.split("|")
      assertThat(returnedValidationMessages).hasSize(expectedExceptions.size)
      expectedExceptions.forEachIndexed { index, exception ->
        assertThat(returnedValidationMessages[index].code.toString()).isEqualTo(exception)
        calculationTestFile.expectedValidationMessage?.let {
          assertThat(
            returnedValidationMessages[index].message,
          ).isEqualTo(it)
        }
      }
    } else if (returnedValidationMessages.isNotEmpty()) {
      fail("Validation messages were returned: $returnedValidationMessages")
    } else {
      val bookingData = jsonTransformation.loadCalculationResult("$example")
      val result = bookingData.first
      assertEquals(
        result.dates.entries.sortedBy { it.key },
        calculatedReleaseDates.calculationResult.dates.entries.sortedBy { it.key },
      )
      assertEquals(
        result.effectiveSentenceLength,
        calculatedReleaseDates.calculationResult.effectiveSentenceLength,
      )
      if (calculationTestFile.assertSds40 == true) {
        assertEquals(result.affectedBySds40, calculatedReleaseDates.calculationResult.affectedBySds40)
      }
      if (bookingData.second.contains("sdsEarlyReleaseAllocatedTranche")) {
        assertEquals(
          result.sdsEarlyReleaseAllocatedTranche,
          calculatedReleaseDates.calculationResult.sdsEarlyReleaseAllocatedTranche,
        )
        assertEquals(
          result.sdsEarlyReleaseTranche,
          calculatedReleaseDates.calculationResult.sdsEarlyReleaseTranche,
        )
      }
    }
  }

  fun `Test UX Example Breakdowns`(
    example: String,
  ) {
    CalculationTransactionalServiceTest.Companion.log.info("Testing example $example")
    val calculationTestFile = jsonTransformation.loadCalculationTestFile("overall_calculation/$example")
    overrideFeatureTogglesForTest(calculationTestFile, featureToggles)
    val calculation = jsonTransformation.loadCalculationResult("$example").first

    val calculationBreakdown: CalculationBreakdown?
    try {
      calculationBreakdown = calculationTransactionalService.calculateWithBreakdown(
        calculationTestFile.booking,
        CalculatedReleaseDates(
          calculation.dates,
          -1,
          -1,
          "",
          PRELIMINARY,
          calculationReference = UUID.randomUUID(),
          calculationReason = CALCULATION_REASON,
          calculationDate = LocalDate.of(2024, 1, 1),
        ),
        calculationTestFile.userInputs,
      )
    } catch (e: Exception) {
      if (!calculationTestFile.error.isNullOrEmpty()) {
        Assertions.assertEquals(calculationTestFile.error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    CalculationTransactionalServiceTest.Companion.log.info(
      "Example $example outcome CalculationBreakdown: {}",
      TestUtil.objectMapper().writeValueAsString(calculationBreakdown),
    )
    val actualJson: String? = TestUtil.objectMapper().writeValueAsString(calculationBreakdown)
    val expectedJson: String =
      jsonTransformation.getJsonTest("$example.json", "calculation_breakdown_response")

    JSONAssert.assertEquals(
      expectedJson,
      actualJson,
      JSONCompareMode.LENIENT,
    )

    assertThat(calculationBreakdown).isEqualTo(jsonTransformation.loadCalculationBreakdown("$example"))
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun getTestCasesFromDir(dir: File, excluded: List<String>, params: String?): Stream<Arguments> {
      val args = mutableListOf<String>()
      JsonTransformation().doAllInDir(
        dir,
      ) {
        val arg = it.path.replace(dir.path + "/", "").replace(".json", "")
        if (!excluded.contains(arg)) {
          if (params != null) {
            try {
              val file = JsonTransformation().loadCalculationTestFile("overall_calculation/$arg")
              if (file.params == params) {
                args.add(arg)
              }
            } catch (e: Exception) {
              // Error with the JSON test file. Ignore it here and it will be picked up and ran within the test with a better log message.
              log.error("FAILED TO LOAD FILE: $arg")
              args.add(arg)
            }
          } else {
            args.add(arg)
          }
        }
      }
      return args.distinct().stream().map { Arguments.of(it) }
    }
  }
}
