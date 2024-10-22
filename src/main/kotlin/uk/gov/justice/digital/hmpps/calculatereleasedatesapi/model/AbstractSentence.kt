package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.LocalDate
import java.util.*

/**
 * A sentence that was imposed. This could be any sentence type and could be a single sentence as part of a consecutive
 * chain.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type",
  defaultImpl = StandardDeterminateSentence::class,
)
@JsonSubTypes(
  JsonSubTypes.Type(value = StandardDeterminateSentence::class, name = "StandardSentence"),
  JsonSubTypes.Type(value = ExtendedDeterminateSentence::class, name = "ExtendedDeterminateSentence"),
  JsonSubTypes.Type(value = SopcSentence::class, name = "SopcSentence"),
  JsonSubTypes.Type(value = AFineSentence::class, name = "AFineSentence"),
  JsonSubTypes.Type(value = DetentionAndTrainingOrderSentence::class, name = "DetentionAndTrainingOrderSentence"),
  JsonSubTypes.Type(value = BotusSentence::class, name = "BotusSentence"),
)
abstract class AbstractSentence(
  override val offence: Offence,
  override val sentencedAt: LocalDate,
  override val identifier: UUID = UUID.randomUUID(),
  // Sentence UUIDS that this sentence is consecutive to.
  open val consecutiveSentenceUUIDs: List<UUID> = listOf(),
  open val caseSequence: Int? = null,
  open val lineSequence: Int? = null,
  open val caseReference: String? = null,
  override val recallType: RecallType? = null,
) : CalculableSentence {
  @JsonIgnore
  override lateinit var sentenceCalculation: SentenceCalculation

  @JsonIgnore
  override lateinit var identificationTrack: SentenceIdentificationTrack

  @JsonIgnore
  override lateinit var releaseDateTypes: ReleaseDateTypes

  override fun calculateErsed(): Boolean = identificationTrack.calculateErsed()

  override fun isCalculationInitialised(): Boolean {
    return this::sentenceCalculation.isInitialized
  }
  override fun isIdentificationTrackInitialized(): Boolean {
    return this::identificationTrack.isInitialized
  }
}
