package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAdditionalInfo.NoAdjustmentAdditionalInfo
import java.time.LocalDate

data class Adjustment(
  val appliesToSentencesFrom: LocalDate,
  val numberOfDays: Int,
  val fromDate: LocalDate? = null,
  val toDate: LocalDate? = null,
  val additionalInfo: AdjustmentAdditionalInfo = NoAdjustmentAdditionalInfo,
)
