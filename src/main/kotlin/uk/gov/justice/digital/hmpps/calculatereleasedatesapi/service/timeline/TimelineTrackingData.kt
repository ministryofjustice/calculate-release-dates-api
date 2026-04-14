package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.ApplicableSDSLegislations
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.FTRLegislation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.LegislationName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config.PreLegislationCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.TrancheName
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import java.time.LocalDate

data class TimelineTrackingData(
  val futureData: TimelineFutureData,
  val calculationsByDate: Map<LocalDate, List<TimelineCalculationEvent>>,
  var latestRelease: Pair<LocalDate, CalculableSentence>,
  val returnToCustodyDate: LocalDate?,
  val offender: Offender,
  val options: CalculationOptions,
  val externalMovements: ExternalMovementTimeline,

  val releasedSentenceGroups: MutableList<SentenceGroup> = mutableListOf(),
  val currentSentenceGroup: MutableList<CalculableSentence> = mutableListOf(),
  val licenceSentences: MutableList<CalculableSentence> = mutableListOf(),
  val expiredLicenceSentences: MutableList<CalculableSentence> = mutableListOf(),

  val previousUalPeriods: MutableList<Pair<LocalDate, LocalDate>> = mutableListOf(),

  var padas: Long = 0,
  var beforeTrancheCalculation: PreLegislationCalculation? = null,

  var applicableFtrLegislation: ApplicableLegislation<FTRLegislation>? = null,
  val applicableSdsLegislations: ApplicableSDSLegislations = ApplicableSDSLegislations(),
  val trancheAllocationByLegislationName: MutableMap<LegislationName, TrancheName> = mutableMapOf(),
) {

  lateinit var latestCalculation: CalculationResult
}
