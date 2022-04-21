package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Booking(
  var offender: Offender,
  val sentences: List<AbstractSentence>,
  val adjustments: Adjustments = Adjustments(),
  val returnToCustodyDate: LocalDate? = null,
  val bookingId: Long = -1L,
) {
  @JsonIgnore
  @Transient
  lateinit var consecutiveSentences: List<AbstractConsecutiveSentence<out AbstractSentence>>

  @JsonIgnore
  @Transient
  var singleTermSentence: SingleTermSentence? = null

  @JsonIgnore
  fun getAllExtractableSentences(): List<ExtractableSentence> {
    val extractableSentences: MutableList<ExtractableSentence> = consecutiveSentences.toMutableList()
    if (singleTermSentence != null) {
      extractableSentences.add(singleTermSentence!!)
    }
    sentences.forEach {
      if (consecutiveSentences.none { consecutive -> consecutive.orderedStandardSentences.contains(it) } &&
        singleTermSentence?.standardSentences?.contains(it) != true
      ) {
        extractableSentences.add(it)
      }
    }
    return extractableSentences.toList()
  }
}
