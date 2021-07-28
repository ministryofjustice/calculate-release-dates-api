package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfileCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class JsonTransformation(
  private var gson: Gson? = GsonBuilder()
    .setPrettyPrinting()
    .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter().nullSafe())
    .create()
) {

  fun loadOffender(testData: String): Offender {
    val json = getJsonTest("$testData.json", "offender")
    return jsonToOffender(json)!!
  }

  fun loadSentence(testData: String): Sentence {
    val json = getJsonTest("$testData.json", "sentence")
    return jsonToSentence(json)!!
  }

  fun loadOffenderSentenceProfile(testData: String): OffenderSentenceProfile {
    val json = getJsonTest("$testData.json", "overall_calculation")
    return jsonToOffenderSentenceProfile(json)!!
  }

  fun loadOffenderSentenceProfileCalculation(testData: String): OffenderSentenceProfileCalculation {
    val json = getJsonTest("$testData.json", "overall_calculation_response")
    return jsonToOffenderSentenceProfileCalculation(json)!!
  }

  private fun jsonToSentence(json: String): Sentence? {
    return gson?.fromJson(json, Sentence::class.java)
  }

  private fun jsonToOffender(json: String): Offender? {
    return gson?.fromJson(json, Offender::class.java)
  }

  private fun jsonToOffenderSentenceProfile(json: String): OffenderSentenceProfile? {
    return gson?.fromJson(json, OffenderSentenceProfile::class.java)
  }

  private fun jsonToOffenderSentenceProfileCalculation(json: String): OffenderSentenceProfileCalculation? {
    return gson?.fromJson(json, OffenderSentenceProfileCalculation::class.java)
  }

  fun offenderSentenceProfileToJson(offenderSentenceProfile: OffenderSentenceProfile): String? {
    return gson?.toJson(offenderSentenceProfile)
  }

  fun offenderSentenceProfileCalculationToJson(
    offenderSentenceProfileCalculation: OffenderSentenceProfileCalculation
  ): String? {
    return gson?.toJson(offenderSentenceProfileCalculation)
  }

  private fun getJsonTest(fileName: String, calculationType: String): String {
    return getResourceAsText("/test_data/$calculationType/$fileName")
  }

  @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
  private fun getResourceAsText(path: String): String {
    log.info("Loading file: {}", path)
    return object {}.javaClass.getResource(path).readText()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  class LocalDateTypeAdapter : TypeAdapter<LocalDate>() {

    override fun write(out: JsonWriter, value: LocalDate) {
      out.value(DateTimeFormatter.ISO_LOCAL_DATE.format(value))
    }

    override fun read(input: JsonReader): LocalDate = LocalDate.parse(input.nextString())
  }
}
