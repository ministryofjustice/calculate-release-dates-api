package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate

abstract class AbstractConsecutiveSentence<S : AbstractSentence>(val orderedSentences: List<S>) : IdentifiableSentence, CalculableSentence, ExtractableSentence {
  override val sentencedAt: LocalDate = orderedSentences.minOf(AbstractSentence::sentencedAt)
  override val offence: Offence = orderedSentences.map(AbstractSentence::offence).minByOrNull(Offence::committedAt)!!

  override val recallType: RecallType?
    get() {
      return orderedSentences[0].recallType
    }

  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: List<ReleaseDateType>
}
