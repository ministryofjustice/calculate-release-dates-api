package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails

data class CalculationOriginalData(
  val prisonerDetails: PrisonerDetails?,
  val sentencesAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
)
