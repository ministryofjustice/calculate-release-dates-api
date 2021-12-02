package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


class ConsecutiveSentence(
  override val sentencedAt: LocalDate,
  override val offence: Offence,
  val orderedSentences: List<Sentence>
) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  constructor(orderedSentences: List<Sentence>) :
    this(orderedSentences.minOf(Sentence::sentencedAt),
      orderedSentences.map(Sentence::offence).minByOrNull(Offence::committedAt)!!,
      orderedSentences)
  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation
  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  fun buildString(): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return """
      Consecutive sentence made up of: ${orderedSentences.size} sentences
      Earliest sentence date of: ${sentencedAt.format(formatter)}
      Earliest offence date of: ${offence.committedAt.format(formatter)}
      Days: ${getLengthInDays()}
    """.trimIndent()
  }

  override fun getLengthInDays(): Int {
    var date = sentencedAt
    orderedSentences.forEach {
      date = date.plusDays(it.duration.getLengthInDays(date).toLong())
    }
    return (ChronoUnit.DAYS.between(sentencedAt, date)).toInt()

  }
}
