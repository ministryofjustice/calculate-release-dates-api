package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType

data class Booking(
  var offender: Offender,
  var sentences: List<Sentence>,
  var adjustments: Map<AdjustmentType, Int> = mapOf(),
  val bookingId: Long = -1L,
) {
  @JsonIgnore
  @Transient
  lateinit var consecutiveSentences: List<ConsecutiveSentence>

  @JsonIgnore
  @Transient
  var singleTermSentence: SingleTermSentence? = null

  fun getOrZero(adjustmentType: AdjustmentType): Int {
    return if (adjustments.containsKey(adjustmentType) && adjustments[adjustmentType] != null) {
      adjustments[adjustmentType]!!
    } else {
      0
    }
  }

  @JsonIgnore
  fun getAllExtractableSentences(): List<ExtractableSentence> {
    val extractableSentences: MutableList<ExtractableSentence> = consecutiveSentences.toMutableList()
    if (singleTermSentence != null) {
      extractableSentences.add(singleTermSentence!!)
    }
    sentences.forEach {
      if (consecutiveSentences.none { consecutive -> consecutive.orderedSentences.contains(it) }
        && singleTermSentence?.sentences?.contains(it) != true) {
        extractableSentences.add(it)
      }
    }
    return extractableSentences.toList()
  }
}
