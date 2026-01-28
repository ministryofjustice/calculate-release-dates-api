package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.EarlyReleaseTrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTrancheCategory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalMovement
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import java.time.LocalDate

data class TimelineTrackingData(
  val futureData: TimelineFutureData,
  val calculationsByDate: Map<LocalDate, List<TimelineCalculationDate>>,
  var latestRelease: Pair<LocalDate, CalculableSentence>,
  val returnToCustodyDate: LocalDate?,
  val offender: Offender,
  val options: CalculationOptions,
  val externalMovements: List<ExternalMovement>,

  val releasedSentenceGroups: MutableList<SentenceGroup> = mutableListOf(),
  val currentSentenceGroup: MutableList<CalculableSentence> = mutableListOf(),
  val licenceSentences: MutableList<CalculableSentence> = mutableListOf(),
  val expiredLicenceSentences: MutableList<CalculableSentence> = mutableListOf(),

  val previousUalPeriods: MutableList<Pair<LocalDate, LocalDate>> = mutableListOf(),

  var outOfPrisonStatus: OutOfPrisonStatus? = null,
  var padas: Long = 0,
  var beforeTrancheCalculation: CalculationResult? = null,

  var allocatedEarlyRelease: EarlyReleaseConfiguration? = null,
  var allocatedTranche: EarlyReleaseTrancheConfiguration? = null,
  val trancheAllocationByCategory: MutableMap<SDSEarlyReleaseTrancheCategory, SDSEarlyReleaseTranche> = mutableMapOf(),
) {

  lateinit var latestCalculation: CalculationResult
  lateinit var currentTimelineCalculationDate: TimelineCalculationDate

  fun isInPrison() = outOfPrisonStatus == null
  fun isOutOfPrison() = outOfPrisonStatus != null
}

data class OutOfPrisonStatus(
  val release: ExternalMovement,
  val admission: ExternalMovement?,
)
