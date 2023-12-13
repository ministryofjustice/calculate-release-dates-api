package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class ManualEntryRequest(val selectedManualEntryDates: List<ManualEntrySelectedDate>,
                              val reasonForCalculationId: Long,
                              val otherReasonDescription: String?)
