package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import java.time.LocalDate

@Service
class NomisCommentService {

  fun getNomisComment(
    calculationRequest: CalculationRequest,
    isSpecialistSupport: Boolean,
    approvedDates: List<ManualEntrySelectedDate>?,
  ): String {
    val comment = if (isSpecialistSupport) {
      SPECIALIST_SUPPORT_COMMENT
    } else if (approvedDates?.isNotEmpty() == true) {
      MANUAL_ENTERED_DATES_COMMENT
    } else if (calculationRequest.reasonForCalculation?.isOther == false) {
      DEFAULT_COMMENT
    } else {
      OTHER_COMMENT
    }

    return if (comment == OTHER_COMMENT) {
      comment.format(calculationRequest.calculationReference)
    } else {
      comment.format(
        calculationRequest.reasonForCalculation?.displayName,
        calculationRequest.calculationReference,
      )
    }
  }

  fun getManualNomisComment(
    calculationRequest: CalculationRequest,
    dates: Map<ReleaseDateType, LocalDate?>,
    isGenuineOverride: Boolean,
  ): String {
    val comment = if (isGenuineOverride) {
      MANUALLY_ENTERED_OVERRIDE
    } else if (dates.containsKey(ReleaseDateType.None)) {
      INDETERMINATE_COMMENT
    } else {
      MANUAL_ENTRY_COMMENT
    }
    return comment.format(calculationRequest.reasonForCalculation?.displayName, calculationRequest.calculationReference)
  }

  private companion object {
    private const val DEFAULT_COMMENT =
      "{%s} using the Calculate Release Dates service. The calculation ID is: The calculation ID is: %s"
    private const val OTHER_COMMENT = "Calculated using the Calculate Release Dates service. The calculation ID is: %s"
    private const val MANUAL_ENTERED_DATES_COMMENT =
      "{%s} using the Calculate Release Dates service with manually entered dates. The calculation ID is: %s"
    private const val SPECIALIST_SUPPORT_COMMENT =
      "{%s} using the Calculate release dates service by Specialist Support. The calculation ID is: %s"
    private const val INDETERMINATE_COMMENT =
      "{%s} An Indeterminate (Life) sentence was entered using the Calculate Release Dates service and was intentionally recorded as blank. The calculation ID is: %s"
    private const val MANUAL_ENTRY_COMMENT =
      "{%s} The information shown was manually recorded in the Calculate Release Dates service. The calculation ID is: %s"
    private const val MANUALLY_ENTERED_OVERRIDE =
      "{%s} was manually recorded in the Calculate release dates service by Specialist Support. The calculation ID is: %s"
  }
}
