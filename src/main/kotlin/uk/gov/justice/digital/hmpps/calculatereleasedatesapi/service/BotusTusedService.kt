package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.CRDS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.CRDS_OVERRIDDEN
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.NOMIS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.HistoricalTusedSource.NOMIS_OVERRIDDEN
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricalTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisTusedData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.util.UUID

@Service
class BotusTusedService(
  val calculationRequestRepository: CalculationRequestRepository,
) {

  val uuidRegex = Regex("[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")

  fun identifyTused(nomisTusedData: NomisTusedData): HistoricalTusedData {
    val historicalTusedSource = when {
      !isValidUUID(nomisTusedData.comment) -> {
        if (nomisTusedData.latestOverrideTused == null) NOMIS else NOMIS_OVERRIDDEN
      }
      else -> {
        if (nomisTusedData.latestOverrideTused == null) CRDS else CRDS_OVERRIDDEN
      }
    }

    return HistoricalTusedData(nomisTusedData.getLatestTusedDate(), historicalTusedSource)
  }

  private fun isValidUUID(comment: String?): Boolean {
    if (comment == null) return false

    return try {
      val uuid = uuidRegex.find(comment, 0)?.value ?: return false
      !calculationRequestRepository.findByCalculationReference(UUID.fromString(uuid)).isEmpty
    } catch (e: IllegalArgumentException) {
      false
    }
  }
}
