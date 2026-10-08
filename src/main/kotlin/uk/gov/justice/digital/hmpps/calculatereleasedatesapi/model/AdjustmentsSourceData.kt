package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto

data class AdjustmentsSourceData(val adjustmentsApiData: List<AdjustmentDto>) {
  fun appendOlderBooking(data: AdjustmentsSourceData): AdjustmentsSourceData = AdjustmentsSourceData(
    adjustmentsApiData = this.adjustmentsApiData + data.adjustmentsApiData,
  )
}
