package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult

data class PreLegislationCalculation(val beforeLegislationAppliedCalculationResult: CalculationResult, val legislationApplied: ApplicableLegislation<SDSLegislation>)
