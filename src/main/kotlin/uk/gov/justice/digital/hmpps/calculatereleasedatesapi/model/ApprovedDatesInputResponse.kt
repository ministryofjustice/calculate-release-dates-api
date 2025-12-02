package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

data class ApprovedDatesInputResponse(
  @param:Schema(description = "Whether the user can add approved dates for this prisoner")
  val approvedDatesAvailable: Boolean,
  @param:Schema(description = "If approved dates cannot be added, the reason why")
  val unavailableReason: ApprovedDatesUnavailableReason?,
  @param:Schema(description = "The results of preliminary calculation if approved dates can be added")
  val calculatedReleaseDates: CalculatedReleaseDates?,
  @param:Schema(description = "Previous approved dates for this prisoner if any are found")
  val previousApprovedDates: List<ApprovedDate>,
) {
  companion object {
    fun unavailable(reason: ApprovedDatesUnavailableReason): ApprovedDatesInputResponse = ApprovedDatesInputResponse(
      approvedDatesAvailable = false,
      unavailableReason = reason,
      calculatedReleaseDates = null,
      previousApprovedDates = emptyList(),
    )

    fun available(calculatedReleaseDates: CalculatedReleaseDates): ApprovedDatesInputResponse = ApprovedDatesInputResponse(
      approvedDatesAvailable = true,
      unavailableReason = null,
      calculatedReleaseDates = calculatedReleaseDates,
      previousApprovedDates = emptyList(),
    )
  }
}
