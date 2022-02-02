package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore

data class Booking(
  var offender: Offender,
  val sentences: List<Sentence>,
  val adjustments: Adjustments = Adjustments(),
  val bookingId: Long = -1L,
) {
  @JsonIgnore
  @Transient
  lateinit var consecutiveSentences: List<ConsecutiveSentence>

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
      if (consecutiveSentences.none { consecutive -> consecutive.orderedSentences.contains(it) } &&
        singleTermSentence?.sentences?.contains(it) != true
      ) {
        extractableSentences.add(it)
      }
    }
    return extractableSentences.toList()
  }
}
