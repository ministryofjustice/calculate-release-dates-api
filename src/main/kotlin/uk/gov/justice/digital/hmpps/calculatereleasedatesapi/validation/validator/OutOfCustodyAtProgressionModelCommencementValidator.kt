package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.UnlawfullyAtLargeDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.SDSLegislationConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAdditionalInfo.NoAdjustmentAdditionalInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import java.time.LocalDate

@Component
class OutOfCustodyAtProgressionModelCommencementValidator(val sdsLegislationConfiguration: SDSLegislationConfiguration) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> = if (wasAssignedAProgressionModelTranche(calculationOutput) && wasOutOfCustodyAtProgressionCommencement(booking)) {
    listOf(ValidationMessage(ValidationCode.UNSUPPORTED_OUT_OF_CUSTODY_AT_PROGRESSION_MODEL_COMMENCEMENT))
  } else {
    emptyList()
  }

  private fun wasAssignedAProgressionModelTranche(calculationOutput: CalculationOutput): Boolean = calculationOutput.calculationResult.trancheAllocationByLegislationName[LegislationName.SDS_PROGRESSION_MODEL] != null

  // currently only considers UAL for out of custody check
  private fun wasOutOfCustodyAtProgressionCommencement(booking: Booking): Boolean {
    val theDayBeforeProgressionCommencement = requireNotNull(sdsLegislationConfiguration.progressionModelLegislation?.commencementDate()?.minusDays(1)) { "Must be set if they were allocated a tranche" }
    val outOfCustodyPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.UNLAWFULLY_AT_LARGE).mapNotNull {
      val reason = when (it.additionalInfo) {
        is NoAdjustmentAdditionalInfo -> null // support older booking data
        is UALAdjustmentAdditionalInfo -> it.additionalInfo.reason
      }
      val fromDate = requireNotNull(it.fromDate) { "UAL adjustment missing from date" }
      val toDate = requireNotNull(it.toDate) { "UAL adjustment missing to date" }
      when (reason) {
        // For a release in error the first day is the day after release last day of UAL is the day before arrest so they're not actually in custody the day before or the following day
        UnlawfullyAtLargeDto.Type.RELEASE_IN_ERROR -> OutOfCustodyPeriod(fromDate.minusDays(1), toDate.plusDays(1))
        // When sentenced in absence or for immigration detention the last day of UAL is the sentence date and the arrest date is the day after so they're not actually in custody until the following day
        UnlawfullyAtLargeDto.Type.SENTENCED_IN_ABSENCE, UnlawfullyAtLargeDto.Type.IMMIGRATION_DETENTION -> OutOfCustodyPeriod(fromDate, toDate.plusDays(1))
        // When it is an escape before 2008-07-21 then the first day of UAL is the day after escape and the last day is the day before arrest. After 2008-07-21 it changed to be the day of escape to the day of arrest
        UnlawfullyAtLargeDto.Type.ESCAPE -> if (fromDate < LocalDate.of(2008, 7, 21)) {
          OutOfCustodyPeriod(fromDate.minusDays(1), toDate.plusDays(1))
        } else {
          OutOfCustodyPeriod(fromDate, toDate)
        }
        // exclude RECALL and unknown types. All DPS adjustments should have a type and that is enforced before tranche commencement and recalls are not eligible for tranches anyway.
        else -> null
      }
    }
    return outOfCustodyPeriods.any { it.from <= theDayBeforeProgressionCommencement && it.to > theDayBeforeProgressionCommencement }
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED

  private data class OutOfCustodyPeriod(val from: LocalDate, val to: LocalDate)
}
