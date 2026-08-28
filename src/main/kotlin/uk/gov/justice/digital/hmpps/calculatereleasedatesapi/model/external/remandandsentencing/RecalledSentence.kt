package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.remandandsentencing

import java.time.LocalDate
import java.util.UUID

data class RecalledSentence(
  val sentenceUuid: UUID,
  val offenceCode: String,
  val offenceStartDate: LocalDate?,
  val offenceEndDate: LocalDate?,
  val sentenceDate: LocalDate?,
  val lineNumber: String?,
  val countNumber: String?,
  val periodLengths: List<PeriodLength>,
  val sentenceServeType: String,
  val sentenceTypeDescription: String?,
  val consecutiveToSentenceUuid: UUID? = null,
  val aggravatingFactors: List<AggravatingFactor> = emptyList(),
)
