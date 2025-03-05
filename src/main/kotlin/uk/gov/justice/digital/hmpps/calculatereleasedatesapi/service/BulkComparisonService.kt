package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison

interface BulkComparisonService {

  fun processPrisonComparison(comparison: Comparison, token: String)

  fun processFullCaseLoadComparison(comparison: Comparison, token: String)

  fun processManualComparison(comparison: Comparison, prisonerIds: List<String>, token: String)
}
