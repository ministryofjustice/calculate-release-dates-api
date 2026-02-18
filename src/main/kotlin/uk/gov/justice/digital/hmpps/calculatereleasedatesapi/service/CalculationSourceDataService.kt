package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.FixedTermRecallDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import java.time.LocalDate

@Service
class CalculationSourceDataService(
  private val prisonService: PrisonService,
  private val featureToggles: FeatureToggles,
  private val botusTusedService: BotusTusedService,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {

  fun getCalculationSourceData(prisonerId: String, sourceDataLookupOptions: SourceDataLookupOptions, olderBookingsToInclude: List<Long> = emptyList()): CalculationSourceData {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    return getCalculationSourceData(prisonerDetails, sourceDataLookupOptions, olderBookingsToInclude)
  }

  fun getCalculationSourceData(prisonerDetails: PrisonerDetails, sourceDataLookupOptions: SourceDataLookupOptions, olderBookingsToInclude: List<Long> = emptyList()): CalculationSourceData {
    var data = getBookingLevelSourceData(prisonerDetails, prisonerDetails.bookingId, sourceDataLookupOptions)
    olderBookingsToInclude.forEach {
      data = data.appendOlderBooking(getBookingLevelSourceData(prisonerDetails, it, sourceDataLookupOptions, olderBooking = true, newerBookingSentences = data.sentenceAndOffences.map { DistinctSentenceData(it) }))
    }
    return getCalculationSourceData(prisonerDetails, data)
  }

  private fun getBookingLevelSourceData(
    prisonerDetails: PrisonerDetails,
    bookingId: Long,
    sourceDataLookupOptions: SourceDataLookupOptions,
    olderBooking: Boolean = false,
    newerBookingSentences: List<DistinctSentenceData> = emptyList(),
  ): BookingLevelSourceData {
    val activeOnly = sourceDataLookupOptions.activeOnly(featureToggles.supportInactiveSentencesAndAdjustments)

    val sentenceAndOffences = removeDuplicatedSentencesFromOlderBooking(prisonService.getSentencesAndOffences(bookingId, activeOnly), olderBooking, newerBookingSentences)
    val adjustments = getAdjustments(prisonerDetails.offenderNo, bookingId, activeOnly, olderBooking, sourceDataLookupOptions)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val (ftrDetails, returnToCustodyDate) = prisonService.getFixedTermRecallDetails(bookingId, bookingHasFixedTermRecall)
    val bookingHasAFine = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.AFine }
    val offenderFinePayments = if (bookingHasAFine) prisonService.getOffenderFinePayments(bookingId) else listOf()
    return BookingLevelSourceData(
      sentenceAndOffences,
      adjustments,
      offenderFinePayments,
      returnToCustodyDate,
      ftrDetails,
    )
  }

  /**
   * When loading data from an older booking remove all sentences in a given court case where there is duplicated sentence on a newer booking.
   */
  private fun removeDuplicatedSentencesFromOlderBooking(
    olderBookingSentences: List<SentenceAndOffenceWithReleaseArrangements>,
    olderBooking: Boolean,
    newerBookingSentences: List<DistinctSentenceData>,
  ): List<SentenceAndOffenceWithReleaseArrangements> {
    if (olderBooking) {
      val caseSequencesWithDuplicates = olderBookingSentences.filter { newerBookingSentences.contains(DistinctSentenceData(it)) }.map { it.caseSequence }.distinct()
      return olderBookingSentences.filterNot { caseSequencesWithDuplicates.contains(it.caseSequence) }
    }
    return olderBookingSentences
  }

  private fun getCalculationSourceData(prisonerDetails: PrisonerDetails, bookingLevelSourceData: BookingLevelSourceData): CalculationSourceData {
    val bookingHasBotus = !featureToggles.applyPostRecallRepealRules && bookingLevelSourceData.sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.Botus }
    val tusedData = if (bookingHasBotus) prisonService.getLatestTusedDataForBotus(prisonerDetails.offenderNo).getOrNull() else null
    val historicalTusedData = tusedData?.let { botusTusedService.identifyTused(it) }
    val externalMovements = prisonService.getExternalMovements(bookingLevelSourceData.sentenceAndOffences, prisonerDetails.offenderNo)

    return CalculationSourceData(
      bookingLevelSourceData.sentenceAndOffences,
      prisonerDetails,
      bookingLevelSourceData.adjustments,
      bookingLevelSourceData.offenderFinePayments,
      bookingLevelSourceData.returnToCustodyDate,
      bookingLevelSourceData.fixedTermRecallDetails,
      historicalTusedData,
      externalMovements,
    )
  }

  private fun getAdjustments(
    prisonerId: String,
    bookingId: Long,
    activeOnly: Boolean,
    isOldBooking: Boolean,
    lookupOptions: SourceDataLookupOptions,
  ): AdjustmentsSourceData = if (lookupOptions.useAdjustmentsApi(featureToggles.useAdjustmentsApi)) {
    val statuses = if (activeOnly) {
      listOf(AdjustmentDto.Status.ACTIVE)
    } else {
      listOf(AdjustmentDto.Status.ACTIVE, AdjustmentDto.Status.INACTIVE)
    }
    AdjustmentsSourceData(adjustmentsApiData = adjustmentsApiClient.getAdjustmentsByPerson(prisonerId, statuses, currentPeriodOfCustody = !isOldBooking).filter { it.bookingId == bookingId })
  } else {
    AdjustmentsSourceData(prisonApiData = prisonService.getBookingAndSentenceAdjustments(bookingId, activeOnly))
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

/**
 * Class to represent if inactive data should be included within calculation.
 *
 * 1. Inactive data is always included for OUT prisoners.
 * 2. The default behaviour is defined by a feature toggle
 * 3. Some endpoints override this behaviour because they require inactive data e.g. recalls, IR
 */

data class SourceDataLookupOptions(
  val overrideToIncludeInactiveData: Boolean = false,
  val overrideToUseAdjustmentsApi: Boolean = false,
) {

  fun activeOnly(featureToggle: Boolean): Boolean = !includeInactive(featureToggle)

  private fun includeInactive(featureToggle: Boolean): Boolean = if (overrideToIncludeInactiveData) {
    true
  } else {
    featureToggle
  }

  fun useAdjustmentsApi(featureToggle: Boolean): Boolean = if (overrideToUseAdjustmentsApi) {
    true
  } else {
    featureToggle
  }

  companion object {
    fun default(): SourceDataLookupOptions = SourceDataLookupOptions(false)
    fun overrideToIncludeInactiveData(): SourceDataLookupOptions = SourceDataLookupOptions(true)
    fun overrideToIncludeInactiveDataAndForceAdjustmentsApi(): SourceDataLookupOptions = SourceDataLookupOptions(true, true)
  }
}

data class DistinctSentenceData(
  val sentenceDate: LocalDate,
  val offenceStartDate: LocalDate?,
  val offenceEndDate: LocalDate? = null,
  val offenceCode: String,
) {
  constructor(sentence: SentenceAndOffenceWithReleaseArrangements) : this(
    sentenceDate = sentence.sentenceDate,
    offenceStartDate = sentence.offence.offenceStartDate,
    offenceEndDate = sentence.offence.offenceEndDate,
    offenceCode = sentence.offence.offenceCode,
  )
}

data class BookingLevelSourceData(
  val sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
  val adjustments: AdjustmentsSourceData,
  val offenderFinePayments: List<OffenderFinePayment> = listOf(),
  val returnToCustodyDate: ReturnToCustodyDate?,
  val fixedTermRecallDetails: FixedTermRecallDetails? = null,
) {

  fun appendOlderBooking(data: BookingLevelSourceData): BookingLevelSourceData = copy(
    sentenceAndOffences = this.sentenceAndOffences + data.sentenceAndOffences,
    adjustments = this.adjustments.appendOlderBooking(data.adjustments),
    offenderFinePayments = this.offenderFinePayments + data.offenderFinePayments,
    returnToCustodyDate = this.returnToCustodyDate ?: data.returnToCustodyDate,
    fixedTermRecallDetails = this.fixedTermRecallDetails ?: data.fixedTermRecallDetails,
  )
}
