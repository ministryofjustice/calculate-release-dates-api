package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfile
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderSentenceProfileCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID

class JsonTransformation {

  var moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .add(LocalDate::class.java, LocalDateAdapter().nullSafe())
    .add(OptionalLocalDateAdapter())
    .add(UUID::class.java, UUIDAdapter().nullSafe())
    .build()

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
    val jsonAdapter = moshi.adapter(Sentence::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToOffender(json: String): Offender? {
    val jsonAdapter = moshi.adapter(Offender::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToOffenderSentenceProfile(json: String): OffenderSentenceProfile? {
    val jsonAdapter = moshi.adapter(OffenderSentenceProfile::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToOffenderSentenceProfileCalculation(json: String): OffenderSentenceProfileCalculation? {
    val jsonAdapter = moshi.adapter(OffenderSentenceProfileCalculation::class.java)
    return jsonAdapter.fromJson(json)
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

  class LocalDateAdapter : JsonAdapter<LocalDate>() {
    override fun toJson(writer: JsonWriter, value: LocalDate?) {
      value?.let { writer.value(it.format(formatter)) }
    }

    override fun fromJson(reader: JsonReader): LocalDate? {
      return if (reader.peek() != JsonReader.Token.NULL) {
        fromNonNullString(reader.nextString())
      } else {
        reader.nextNull<Any>()
        null
      }
    }
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private fun fromNonNullString(nextString: String): LocalDate = LocalDate.parse(nextString, formatter)
  }

  class UUIDAdapter : JsonAdapter<UUID>() {
    override fun toJson(writer: JsonWriter, value: UUID?) {
      value?.let { writer.value(it.toString()) }
    }

    override fun fromJson(jsonReader: JsonReader): UUID? {
      val value = jsonReader.nextString()
      return UUID.fromString(value)
    }
  }

  class OptionalLocalDateAdapter : JsonAdapter<Optional<LocalDate>>() {
    @ToJson
    override fun toJson(writer: JsonWriter, value: Optional<LocalDate>?) {
      value?.let {
        if (value.isEmpty) {
          writer.value("")
        } else {
          writer.value(value.get().format(formatter))
        }
      }
    }

    @FromJson
    override fun fromJson(reader: JsonReader): Optional<LocalDate> {
      return if (reader.peek() != JsonReader.Token.NULL) {
        fromNonNullString(reader.nextString())
      } else {
        Optional.empty<LocalDate>()
      }
    }
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private fun fromNonNullString(nextString: String): Optional<LocalDate> =
      Optional.of(LocalDate.parse(nextString, formatter))
  }
}
