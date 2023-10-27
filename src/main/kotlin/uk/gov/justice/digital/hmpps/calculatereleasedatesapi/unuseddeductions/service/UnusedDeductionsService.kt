package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import org.springframework.stereotype.Service

@Service
class UnusedDeductionsService {

    fun handleMessage(message: AdjustmentCreatedEvent) {
        if (message.additionalInformation.source == "DPS" && !message.additionalInformation.effectiveDays) {

        }
    }
}
data class AdditionalInformation(
    val id: String,
    val offenderNo: String,
    val source: String,
    val effectiveDays: Boolean
)

data class AdjustmentCreatedEvent(
    val additionalInformation: AdditionalInformation,
)