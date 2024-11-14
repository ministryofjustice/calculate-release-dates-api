package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranche
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CustodialPeriod
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import java.time.LocalDate

data class TimelineTrackingData(
  val futureData: TimelineFutureData,
  val calculationsByDate: Map<LocalDate, List<TimelineCalculationDate>>,
  var latestRelease: Pair<LocalDate, CalculableSentence>,
  val returnToCustodyDate: LocalDate?,
  val offender: Offender,
  val options: CalculationOptions,
  val releasedSentences: MutableList<CustodialPeriod> = emptyList<CustodialPeriod>().toMutableList(),
  val custodialSentences: MutableList<CalculableSentence> = emptyList<CalculableSentence>().toMutableList(),
  val licenseSentences: MutableList<CalculableSentence> = emptyList<CalculableSentence>().toMutableList(),

  var trancheAndCommencement: Pair<SDSEarlyReleaseTranche, LocalDate?> = SDSEarlyReleaseTranche.TRANCHE_0 to null,
  var padas: Long = 0,
  var beforeTrancheCalculation: CalculationResult? = null,
) {

  lateinit var latestCalculation: CalculationResult
}
