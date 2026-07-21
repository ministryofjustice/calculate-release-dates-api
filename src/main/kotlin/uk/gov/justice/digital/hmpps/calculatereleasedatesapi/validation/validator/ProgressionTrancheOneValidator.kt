package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class ProgressionTrancheOneValidator(private val featureToggles: FeatureToggles) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> = if (
    featureToggles.progressionTrancheOneManualJourney &&
    wasAssignedAProgressionModelTranche(calculationOutput) &&
    isAssignedProgressionModelTrancheOne(calculationOutput)
  ) {
    listOf(ValidationMessage(ValidationCode.PROGRESSION_TRANCHE_ONE_ALLOCATION))
  } else {
    emptyList()
  }

  private fun wasAssignedAProgressionModelTranche(calculationOutput: CalculationOutput): Boolean = calculationOutput.calculationResult.trancheAllocationByLegislationName[LegislationName.SDS_PROGRESSION_MODEL] != null

  private fun isAssignedProgressionModelTrancheOne(calculationOutput: CalculationOutput): Boolean = calculationOutput.calculationResult.trancheAllocationByLegislationName[LegislationName.SDS_PROGRESSION_MODEL] == TrancheName.TRANCHE_1

  override fun validationOrder(): ValidationOrder = ValidationOrder.UNSUPPORTED
}
