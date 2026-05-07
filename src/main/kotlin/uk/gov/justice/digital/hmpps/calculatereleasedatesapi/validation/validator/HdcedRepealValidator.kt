package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementDirection
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovementReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class HdcedRepealValidator(val sdsLegislationConfiguration: SDSLegislationConfiguration) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    if (sdsLegislationConfiguration.progressionModelLegislation == null) return emptyList()

    if (calculationOutput.calculationResult.dates.contains(ReleaseDateType.HDCED)) {
      val hdcedDate = calculationOutput.calculationResult.dates[ReleaseDateType.HDCED]

      val lastHdcRelease = booking.externalMovements
        .filter { it.direction == ExternalMovementDirection.OUT && it.movementReason == ExternalMovementReason.HDC }
        .maxByOrNull { it.movementDate }

      if (
        lastHdcRelease != null &&
        booking.externalMovements.none {
          it.direction == ExternalMovementDirection.IN && it.movementDate.isAfterOrEqualTo(lastHdcRelease.movementDate)
        }
      ) {
        return listOf(
          ValidationMessage(ValidationCode.HDCED_REPEAL, listOf(hdcedDate.toString())),
        )
      }
    }

    return emptyList()
  }

  override fun validationOrder(): ValidationOrder = ValidationOrder.UNSUPPORTED
}
