package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class PreviouslyRecordedSLED(
  @param:Schema(description = "The SLED that has been used instead of the calculated one.")
  val previouslyRecordedSLEDDate: LocalDate,
  @param:Schema(description = "The SLED that was calculated but is not being used.")
  val calculatedDate: LocalDate,
  @param:Schema(description = "The calculation request id of the calculation containing the previously recorded SLED")
  val previouslyRecordedSLEDCalculationRequestId: Long,
)
