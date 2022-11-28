package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Booking(
  var offender: Offender,
  val sentences: List<AbstractSentence>,
  val adjustments: Adjustments = Adjustments(),
  val returnToCustodyDate: LocalDate? = null,
  val bookingId: Long = -1L,
  val calculateErsed: Boolean = false
) {
  @JsonIgnore
  @Transient
  lateinit var consecutiveSentences: List<ConsecutiveSentence>

  @JsonIgnore
  @Transient
  var singleTermSentence: SingleTermSentence? = null

  @JsonIgnore
  @Transient
  lateinit var sentenceGroups: List<List<CalculableSentence>>

  @JsonIgnore
  fun getAllExtractableSentences(): List<CalculableSentence> {
    val extractableSentences: MutableList<CalculableSentence> = consecutiveSentences.toMutableList()
    if (singleTermSentence != null) {
      extractableSentences.add(singleTermSentence!!)
    }
    sentences.forEach {
      if (consecutiveSentences.none { consecutive -> consecutive.orderedSentences.contains(it) } &&
        singleTermSentence?.standardSentences?.contains(it) != true
      ) {
        extractableSentences.add(it)
      }
    }
    return extractableSentences.toList()
  }
}
