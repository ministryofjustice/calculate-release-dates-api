package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import java.io.File
import java.io.FileNotFoundException

class JsonTransformation {

  val mapper = TestUtil.objectMapper()

  fun loadOffender(testData: String): Offender {
    val json = getJsonTest("$testData.json", "offender")
    return mapper.readValue(json, Offender::class.java)
  }

  fun loadSentence(testData: String): StandardDeterminateSentence {
    val json = getJsonTest("$testData.json", "sentence")
    return mapper.readValue(json, StandardDeterminateSentence::class.java)
  }

  fun loadBooking(testData: String): Booking {
    val json = getJsonTest("$testData.json", "overall_calculation")
    return mapper.readValue(json, Booking::class.java)
  }

  fun loadCalculationResult(testData: String): CalculationResult {
    val json = getJsonTest("$testData.json", "overall_calculation_response")
    return mapper.readValue(json, CalculationResult::class.java)
  }

  fun loadCalculationBreakdown(testData: String): CalculationBreakdown {
    val json = getJsonTest("$testData.json", "calculation_breakdown_response")
    return mapper.readValue(json, CalculationBreakdown::class.java)
  }

  fun getAllPrisonerDetails(): Map<String, PrisonerDetails> {
    return getAllJsonFromDir("api_integration/prisoners")
      .mapValues { mapper.readValue(it.value, PrisonerDetails::class.java) }
  }

  fun getAllSentenceAndOffencesJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/sentences")
  }

  fun getAllAdjustmentsJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/adjustments")
  }

  fun getAllReturnToCustodyDatesJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/returntocustody")
  }

  fun getJsonTest(fileName: String, calculationType: String): String {
    return getResourceAsText("/test_data/$calculationType/$fileName")
  }

  fun getAllJsonFromDir(fileName: String): Map<String, String> {
    val dir = File(object {}.javaClass.getResource("/test_data/$fileName").file)
    if (dir.isDirectory) {
      val json = mutableMapOf<String, String>()
      dir.walk().forEach {
        if (it.extension == "json") {
          json[it.nameWithoutExtension] = it.readText()
        }
      }
      return json
    } else {
      throw FileNotFoundException("File $fileName was not a directory")
    }
  }

  @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  private fun getResourceAsText(path: String): String {
    log.info("Loading file: {}", path)
    return object {}.javaClass.getResource(path).readText()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
