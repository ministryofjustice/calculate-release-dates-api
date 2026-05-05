package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.UnlawfullyAtLargeDto

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true, defaultImpl = AdjustmentAdditionalInfo.NoAdjustmentAdditionalInfo::class)
@JsonSubTypes(
  JsonSubTypes.Type(value = AdjustmentAdditionalInfo.NoAdjustmentAdditionalInfo::class, name = "NONE"),
  JsonSubTypes.Type(value = AdjustmentAdditionalInfo.UALAdjustmentAdditionalInfo::class, name = "UAL"),
)
sealed class AdjustmentAdditionalInfo {
  object NoAdjustmentAdditionalInfo : AdjustmentAdditionalInfo()
  data class UALAdjustmentAdditionalInfo(val reason: UnlawfullyAtLargeDto.Type?) : AdjustmentAdditionalInfo()
}
