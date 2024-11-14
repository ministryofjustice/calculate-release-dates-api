package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.fasterxml.jackson.core.type.TypeReference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DatesAndHints
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

  fun loadBotusSentence(testData: String): BotusSentence {
    val json = getJsonTest("$testData.json", "sentence")
    return mapper.readValue(json, BotusSentence::class.java)
  }

  fun loadBooking(testData: String): Pair<Booking, CalculationUserInputs> {
    val json = getJsonTest("$testData.json", "overall_calculation")
    val jsonTree = mapper.readTree(json)
    val calculateErsed = if (jsonTree.has("calculateErsed")) jsonTree.get("calculateErsed").booleanValue() else false
    return mapper.readValue(json, Booking::class.java) to CalculationUserInputs(calculateErsed = calculateErsed)
  }

  fun loadCalculationResult(testData: String): CalculationResult {
    val json = getJsonTest("$testData.json", "overall_calculation_response")
    return mapper.readValue(json, CalculationResult::class.java)
  }

  fun loadCalculationBreakdown(testData: String): CalculationBreakdown {
    val json = getJsonTest("$testData.json", "calculation_breakdown_response")
    return mapper.readValue(json, CalculationBreakdown::class.java)
  }

  fun loadHintTextBooking(testCase: String): Pair<Booking, CalculationUserInputs> {
    val json = getJsonTest("$testCase.json", "hint-text/input-data")
    val jsonTree = mapper.readTree(json)
    val calculateErsed = if (jsonTree.has("calculateErsed")) jsonTree.get("calculateErsed").booleanValue() else false
    return mapper.readValue(json, Booking::class.java) to CalculationUserInputs(calculateErsed = calculateErsed)
  }

  fun loadHintTextResults(testCase: String): List<DatesAndHints> {
    val json = getJsonTest("$testCase.json", "hint-text/expected-results")
    return mapper.readValue(json, object : TypeReference<List<DatesAndHints>>() {})
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

  fun getAllOffenderFinePaymentsJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/finepayments")
  }

  fun getAllPrisonerCalculableSentenceEnvelopesJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/prisonerCalculableSentenceEnvelope")
  }

  fun getApiIntegrationJson(fileName: String, type: String): String {
    return getResourceAsText("/test_data/api_integration/$type/$fileName.json")
  }

  fun getJsonTest(fileName: String, calculationType: String): String {
    return getResourceAsText("/test_data/$calculationType/$fileName")
  }

  fun getAllJsonFromDir(fileName: String): Map<String, String> {
    val dir = File(object {}.javaClass.getResource("/test_data/$fileName").file)
    if (dir.isDirectory) {
      val json = mutableMapOf<String, String>()
      val originalPath = dir.path + "/"
      dir.walk().forEach {
        if (it.extension == "json") {
          json[it.path.replace(originalPath, "").replace(".json", "")] = it.readText()
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
