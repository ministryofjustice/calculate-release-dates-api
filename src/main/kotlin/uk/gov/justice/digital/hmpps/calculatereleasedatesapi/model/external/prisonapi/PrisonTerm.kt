package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prison Term")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonTerm(
  @Schema(description = "Book Number (Prison) / Prison Number (Probation)", example = "B45232", required = true)
  val bookNumber: String? = null,

  @Schema(description = "Booking Identifier (internal)", example = "12312312", required = true)
  val bookingId: Long? = null,
  val courtSentences: List<CourtSentences>? = null,

  @Schema(description = "Licence sentences")
  val licenceSentences: List<SentencesOffencesTerms>? = null,
  val keyDates: KeyDates? = null,
  val sentenceAdjustments: SentenceAdjustmentDetail? = null,
)
