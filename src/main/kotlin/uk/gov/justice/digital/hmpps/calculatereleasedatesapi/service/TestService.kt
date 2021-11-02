package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID

//  TODO this is a temporary service to aid diagnosis of calculation errors whilst in private beta - this whole
//   service will eventually be removed
@Service
class TestService {
  var moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .add(LocalDate::class.java, LocalDateAdapter().nullSafe())
    .add(OptionalLocalDateAdapter())
    .add(UUID::class.java, UUIDAdapter().nullSafe())
    .build()

  fun jsonToBooking(json: String): Booking {
    val jsonAdapter = moshi.adapter(Booking::class.java)
    return jsonAdapter.fromJson(json)!!
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
