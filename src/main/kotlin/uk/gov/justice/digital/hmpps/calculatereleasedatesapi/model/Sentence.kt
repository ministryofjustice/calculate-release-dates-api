package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToLong

data class Sentence(
  override val offence: Offence,
  val duration: Duration,
  override val sentencedAt: LocalDate,
  val identifier: UUID = UUID.randomUUID(),
  // Sentence UUIDS that this sentence is consecutive to.
  val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  val caseSequence: Int? = null,
  val lineSequence: Int? = null
) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  @JsonIgnore
  @Transient
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  @Transient
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  @Transient
  override lateinit var releaseDateTypes: List<ReleaseDateType>

  @JsonIgnore
  fun isSentenceCalculated(): Boolean {
    return this::sentenceCalculation.isInitialized
  }

  fun buildString(): String {
    return "Sentence\t:\t\n" +
      "Duration\t:\t$duration\n" +
      "${duration.toPeriodString(sentencedAt)}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${duration.getLengthInDays(sentencedAt)}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    return duration.getLengthInDays(this.sentencedAt)
  }
}
