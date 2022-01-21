package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isBeforeOrEqualTo
import java.time.LocalDate

data class Booking(
  var offender: Offender,
  val sentences: List<Sentence>,
  val adjustments: Map<AdjustmentType, List<Adjustment>> = mapOf(),
  val bookingId: Long = -1L,
) {
  @JsonIgnore
  @Transient
  lateinit var consecutiveSentences: List<ConsecutiveSentence>

  @JsonIgnore
  @Transient
  var singleTermSentence: SingleTermSentence? = null

  fun getOrZero(adjustmentType: AdjustmentType, adjustmentsFrom: LocalDate): Int {
    if (adjustments.containsKey(adjustmentType) && adjustments[adjustmentType] != null) {
      val adjustments = adjustments[adjustmentType]!!
      val adjustmentDays = adjustments.filter { it.appliesToSentencesFrom.isBeforeOrEqualTo(adjustmentsFrom) }
        .map { it.numberOfDays }
      if (adjustmentDays.isNotEmpty()) {
        return adjustmentDays.reduce { acc, it -> acc + it }
      }
    }
    return 0
  }

  fun get(adjustmentType: AdjustmentType): List<Adjustment> {
    return adjustments.getOrDefault(adjustmentType, listOf())
  }

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
