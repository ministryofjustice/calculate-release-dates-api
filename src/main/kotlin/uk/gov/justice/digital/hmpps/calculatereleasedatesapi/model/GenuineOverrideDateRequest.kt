package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class GenuineOverrideDateRequest(val manualEntryRequest: ManualEntryRequest, val originalCalculationReference: String)
