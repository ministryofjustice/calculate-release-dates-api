package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment

@Schema(description = "The active sentence envelope is a combination of the person information, the active booking and calculable sentences at a particular establishment")
data class CalculableSentenceEnvelope(
  @Schema(description = "The identifiers of a person necessary for a calculation")
  val person: Person,

  @Schema(description = "Most recent term in prison")
  val latestPrisonTerm: PrisonTerm? = null,

  @Schema(description = "Adjustments at a sentence level")
  val sentenceAdjustments: List<SentenceAdjustmentValues> = listOf(),

  @Schema(description = "Adjustments at a booking level")
  val bookingAdjustments: List<BookingAdjustment> = listOf(),

  @Schema(description = "List of offender fine payments")
  val offenderFinePayments: List<OffenderFinePaymentDto> = listOf(),

  @Schema(description = "Fixed term recall details")
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,

  @Schema(description = "The current set of sentence dates determined by NOMIS or recorded via overrides")
  val sentenceCalcDates: SentenceCalcDates? = null,

)