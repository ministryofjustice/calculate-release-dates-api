package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Sentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID

class JsonTransformation {

  var moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .add(LocalDate::class.java, LocalDateAdapter().nullSafe())
    .add(OptionalLocalDateAdapter())
    .add(UUID::class.java, UUIDAdapter().nullSafe())
    .add(Period::class.java, PeriodAdapter().nullSafe())
    .build()

  fun loadOffender(testData: String): Offender {
    val json = getJsonTest("$testData.json", "offender")
    return jsonToOffender(json)!!
  }

  fun loadSentence(testData: String): Sentence {
    val json = getJsonTest("$testData.json", "sentence")
    return jsonToSentence(json)!!
  }

  fun loadBooking(testData: String): Booking {
    val json = getJsonTest("$testData.json", "overall_calculation")
    return jsonToBooking(json)!!
  }

  fun loadBookingCalculation(testData: String): BookingCalculation {
    val json = getJsonTest("$testData.json", "overall_calculation_response")
    return jsonToBookingCalculation(json)!!
  }

  fun loadCalculationBreakdown(testData: String): CalculationBreakdown {
    val json = getJsonTest("$testData.json", "calculation_breakdown_response")
    return jsonToCalculationBreakdown(json)!!
  }

  fun getAllPrisonerDetails(): Map<String, PrisonerDetails> {
    return getAllJsonFromDir("api_integration/prisoners")
      .mapValues { jsonToPrisonerDetails(it.value)!! }
  }

  fun getAllPrisonerDetailsJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/prisoners")
  }

  fun getAllSentenceAndOffencesJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/sentences")
  }

  fun getAllAdjustmentsJson(): Map<String, String> {
    return getAllJsonFromDir("api_integration/adjustments")
  }

  private fun jsonToSentence(json: String): Sentence? {
    val jsonAdapter = moshi.adapter(Sentence::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToOffender(json: String): Offender? {
    val jsonAdapter = moshi.adapter(Offender::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToBooking(json: String): Booking? {
    val jsonAdapter = moshi.adapter(Booking::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToBookingCalculation(json: String): BookingCalculation? {
    val jsonAdapter = moshi.adapter(BookingCalculation::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToCalculationBreakdown(json: String): CalculationBreakdown? {
    val jsonAdapter = moshi.adapter(CalculationBreakdown::class.java)
    return jsonAdapter.fromJson(json)
  }

  private fun jsonToPrisonerDetails(json: String): PrisonerDetails? {
    val jsonAdapter = moshi.adapter(PrisonerDetails::class.java)
    return jsonAdapter.fromJson(json)
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

  class PeriodAdapter : JsonAdapter<Period>() {
    @ToJson
    override fun toJson(writer: JsonWriter, value: Period?) {
      value?.let { writer.value(it.toString()) }
    }

    @FromJson
    override fun fromJson(jsonReader: JsonReader): Period? {
      @Suppress("UNCHECKED_CAST")
      val jsonMap = jsonReader.readJsonValue() as Map<String, Double>
      val years = if (jsonMap.containsKey("YEARS")) jsonMap["YEARS"] else 0
      val months = if (jsonMap.containsKey("MONTHS")) jsonMap["MONTHS"] else 0
      val days = if (jsonMap.containsKey("DAYS")) jsonMap["DAYS"] else 0
      return Period.of(years!!.toInt(), months!!.toInt(), days!!.toInt())
    }
  }
}
