package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import java.time.LocalDate

data class Booking(
  var offender: Offender,
  val sentences: List<AbstractSentence>,
  val adjustments: Adjustments = Adjustments(),
  // TODO remove and replace with fixedTermRecallDetails
  val returnToCustodyDate: LocalDate? = null,
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
  val bookingId: Long = -1L,
) {
  @JsonIgnore
  lateinit var consecutiveSentences: List<ConsecutiveSentence>

  @JsonIgnore
  var singleTermSentence: SingleTermed? = null

  @JsonIgnore
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

  @JsonIgnore
  val underEighteenAtEndOfCustodialPeriod: () -> Boolean = {
    sentences.all { offender.getAgeOnDate(it.sentenceCalculation.releaseDate) < 18 } && consecutiveSentences.all { offender.getAgeOnDate(it.sentenceCalculation.releaseDate) < 18 }
  }
}
