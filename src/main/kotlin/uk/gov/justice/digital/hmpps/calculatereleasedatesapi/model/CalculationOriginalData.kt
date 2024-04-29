package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence

data class CalculationOriginalData(
  val prisonerDetails: PrisonerDetails?,
  val sentencesAndOffences: List<SentenceAndOffence>?,
)
