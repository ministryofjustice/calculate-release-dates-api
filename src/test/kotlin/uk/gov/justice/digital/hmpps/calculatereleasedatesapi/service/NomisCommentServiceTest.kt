package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import java.time.LocalDate
import java.util.UUID

class NomisCommentServiceTest {

  private val nomisCommentService = NomisCommentService()

  @Test
  fun `Tests for comments produced on the regular route`() {
    assertEquals(
      "{NOMIS_COMMENT} using the Calculate Release Dates service. The calculation ID is: The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getNomisComment(CALCULATION_REQUEST, approvedDates = null),
      "If the default comment is selected the reason is captured in the NOMIS comment",
    )

    assertEquals(
      "{NOMIS_COMMENT} using the Calculate Release Dates service with manually entered dates. The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getNomisComment(
        CALCULATION_REQUEST,
        approvedDates = listOf(ManuallyEnteredDate(ReleaseDateType.CRD, SubmittedDate(1, 1, 2023))),
      ),
      "If the calculation had manually entered dates this is captured in the NOMIS comment",
    )
  }

  @Test
  fun `If the other reason flag is set it is handled correctly`() {
    assertEquals(
      "Calculated using the Calculate Release Dates service. The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getNomisComment(
        CALCULATION_REQUEST.copy(
          reasonForCalculation = CALCULATION_REASON.copy(
            isOther = true,
          ),
        ),
        approvedDates = null,
      ),
      "If the calculation is not created by specialist support the comment is correct.",
    )
  }

  @Test
  fun `Tests for the reasons for the manual NOMIS comments`() {
    val releaseDates = mutableMapOf<ReleaseDateType, LocalDate?>()
    releaseDates[ReleaseDateType.SED] = LocalDate.of(2026, 1, 1)
    assertEquals(
      "{NOMIS_COMMENT} was manually recorded in the Calculate release dates service by Specialist Support. The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getManualNomisComment(CALCULATION_REQUEST, releaseDates, isGenuineOverride = true),
      "If the calculation request is a Genuine Override then it has the correct comment",
    )

    assertEquals(
      "{NOMIS_COMMENT} The information shown was manually recorded in the Calculate Release Dates service. The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getManualNomisComment(CALCULATION_REQUEST, releaseDates, isGenuineOverride = false),
      "If the calculation request is manually entered then it has the correct comment",
    )

    releaseDates.clear()
    releaseDates[ReleaseDateType.None] = null
    assertEquals(
      "{NOMIS_COMMENT} An Indeterminate (Life) sentence was entered using the Calculate Release Dates service and was intentionally recorded as blank. The calculation ID is: 219db65e-d7b7-4c70-9239-98babff7bcd5",
      nomisCommentService.getManualNomisComment(CALCULATION_REQUEST, releaseDates, isGenuineOverride = false),
      "If the calculation request is indeterminate then it is reflected in the comment",
    )
  }

  private companion object {
    private val CALCULATION_REASON = CalculationReason(
      isBulk = false,
      isActive = true,
      isOther = false,
      displayName = "Reason",
      nomisReason = "REASON",
      nomisComment = "NOMIS_COMMENT",
      displayRank = 10,
    )

    private val CALCULATION_REQUEST = CalculationRequest(
      id = 123456L,
      calculationReference = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5"),
      reasonForCalculation = CALCULATION_REASON,
    )
  }
}
