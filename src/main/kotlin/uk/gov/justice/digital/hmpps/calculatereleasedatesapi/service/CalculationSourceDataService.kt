package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.Companion.from
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments

@Service
class CalculationSourceDataService(
  private val prisonService: PrisonService,
  private val featureToggles: FeatureToggles,
  private val botusTusedService: BotusTusedService,
  private val adjustmentsApiClient: AdjustmentsApiClient,
) {
  //  The activeDataOnly flag is only used by a test endpoint (1000 calcs test, which is used to test historic data)
  fun getCalculationSourceData(prisonerId: String, inactiveDataOptions: InactiveDataOptions): CalculationSourceData {
    val prisonerDetails = prisonService.getOffenderDetail(prisonerId)
    return getCalculationSourceData(prisonerDetails, inactiveDataOptions)
  }

  fun getCalculationSourceData(prisonerDetails: PrisonerDetails, inactiveDataOptions: InactiveDataOptions): CalculationSourceData {
    val activeOnly = inactiveDataOptions.activeOnly(featureToggles.supportInactiveSentencesAndAdjustments)

    val sentenceAndOffences = prisonService.getSentencesAndOffences(prisonerDetails.bookingId, activeOnly)
    val adjustments = getAdjustments(prisonerDetails, sentenceAndOffences, activeOnly)
    val bookingHasFixedTermRecall = sentenceAndOffences.any { from(it.sentenceCalculationType).recallType?.isFixedTermRecall == true }
    val (ftrDetails, returnToCustodyDate) = prisonService.getFixedTermRecallDetails(prisonerDetails.bookingId, bookingHasFixedTermRecall)
    val bookingHasAFine = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.AFine }
    val offenderFinePayments = if (bookingHasAFine) prisonService.getOffenderFinePayments(prisonerDetails.bookingId) else listOf()
    val tusedData = prisonService.getLatestTusedDataForBotus(prisonerDetails.offenderNo).getOrNull()
    val bookingHasBotus = sentenceAndOffences.any { from(it.sentenceCalculationType).sentenceType == SentenceType.Botus }
    val historicalTusedData = if (tusedData != null && bookingHasBotus) botusTusedService.identifyTused(tusedData) else null
    val externalMovements = prisonService.getExternalMovements(sentenceAndOffences, prisonerDetails)

    return CalculationSourceData(
      sentenceAndOffences,
      prisonerDetails,
      adjustments,
      offenderFinePayments,
      returnToCustodyDate,
      ftrDetails,
      historicalTusedData,
      externalMovements,
    )
  }

  private fun getAdjustments(
    prisonerDetails: PrisonerDetails,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>,
    activeOnly: Boolean,
  ): Either<BookingAndSentenceAdjustments, List<AdjustmentDto>> {
    return if (featureToggles.useAdjustmentsApi) {
      val statuses = if (activeOnly) {
        listOf(AdjustmentDto.Status.ACTIVE)
      } else {
        listOf(AdjustmentDto.Status.ACTIVE, AdjustmentDto.Status.INACTIVE)
      }
      val earliestSentenceDate = sentenceAndOffences.minOfOrNull { it.sentenceDate }
      adjustmentsApiClient.getAdjustmentsByPerson(prisonerDetails.offenderNo, earliestSentenceDate, statuses).right()
    } else {
      prisonService.getBookingAndSentenceAdjustments(prisonerDetails.bookingId, activeOnly).left()
    }
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
class InactiveDataOptions private constructor(
  private val overrideToIncludeInactiveData: Boolean,
) {

  fun activeOnly(featureToggle: Boolean): Boolean {
    return !includeInactive(featureToggle)
  }

  private fun includeInactive(featureToggle: Boolean): Boolean {
    return if (overrideToIncludeInactiveData) {
      true
    } else {
      featureToggle
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InactiveDataOptions

    return overrideToIncludeInactiveData == other.overrideToIncludeInactiveData
  }

  override fun hashCode(): Int {
    return overrideToIncludeInactiveData.hashCode()
  }

  companion object {
    fun default(): InactiveDataOptions = InactiveDataOptions(false)
    fun overrideToIncludeInactiveData(): InactiveDataOptions = InactiveDataOptions(true)
  }
}
