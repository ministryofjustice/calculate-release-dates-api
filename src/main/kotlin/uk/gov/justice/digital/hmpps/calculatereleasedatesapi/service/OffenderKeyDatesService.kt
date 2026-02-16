package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.getOrElse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationReasonDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesAndCalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.CalculationController

@Service
@Transactional(readOnly = true)
class OffenderKeyDatesService(
  private val prisonService: PrisonService,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val featureToggles: FeatureToggles,
  private val manageUsersApiClient: ManageUsersApiClient,
) {

  fun getKeyDatesByCalcId(calculationRequestId: Long): ReleaseDatesAndCalculationContext {
    val calculationRequest = runCatching { calculationRequestRepository.findById(calculationRequestId).get() }
      .getOrElse {
        throw CrdWebException(
          "Unable to retrieve offender key dates",
          HttpStatus.NOT_FOUND,
        )
      }

    val offenderKeyDatesEither = prisonService.getOffenderKeyDates(calculationRequest.bookingId).onLeft {
      throw CrdWebException("Unable to retrieve offender key dates", HttpStatus.NOT_FOUND)
    }

    val dates = offenderKeyDatesEither.map { releaseDates(it) }
      .getOrElse { throw CrdWebException("Error in mapping/enriching release dates", HttpStatus.NOT_FOUND) }

    val historicSledOverride = calculationOutcomeHistoricOverrideRepository.findByCalculationRequestId(calculationRequestId)

    val enrichedDates = runCatching {
      calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        null,
        null,
        null,
        null,
        historicSledOverride,
      ).values.toList()
    }.getOrElse { throw CrdWebException("Unable to retrieve offender key dates", HttpStatus.NOT_FOUND) }
    val calculatedAtPrisonDescription: String? = if (calculationRequest.prisonerLocation != null) {
      prisonService.getAgenciesByType("INST").firstOrNull { it.agencyId == calculationRequest.prisonerLocation }?.description ?: calculationRequest.prisonerLocation
    } else {
      null
    }
    return ReleaseDatesAndCalculationContext(
      CalculationContext(
        calculationRequestId = calculationRequestId,
        bookingId = calculationRequest.bookingId,
        prisonerId = calculationRequest.prisonerId,
        calculationStatus = CalculationStatus.CONFIRMED,
        calculationReference = calculationRequest.calculationReference,
        calculationReason = calculationRequest.reasonForCalculation?.let { CalculationReasonDto.from(it) },
        otherReasonDescription = calculationRequest.otherReasonForCalculation,
        calculationDate = calculationRequest.calculatedAt.toLocalDate(),
        calculationType = calculationRequest.calculationType,
        overridesCalculationRequestId = calculationRequest.overridesCalculationRequestId,
        genuineOverrideReasonCode = calculationRequest.genuineOverrideReason,
        genuineOverrideReasonDescription = calculationRequest.genuineOverrideReasonFurtherDetail ?: calculationRequest.genuineOverrideReason?.description,
        usePreviouslyRecordedSLEDIfFound = calculationRequest.calculationRequestUserInput?.usePreviouslyRecordedSLEDIfFound ?: false,
        calculatedByUsername = calculationRequest.calculatedByUsername,
        calculatedByDisplayName = manageUsersApiClient.getUserByUsername(calculationRequest.calculatedByUsername)?.name ?: calculationRequest.calculatedByUsername,
        calculatedAtPrisonId = calculationRequest.prisonerLocation,
        calculatedAtPrisonDescription = calculatedAtPrisonDescription,
      ),
      enrichedDates,
    )
  }

  fun getNomisCalculationSummary(offenderSentCalculationId: Long): NomisCalculationSummary {
    CalculationController.log.info("Request received to get offender key dates for $offenderSentCalculationId")
    val nomisOffenderKeyDates = prisonService.getNOMISOffenderKeyDates(offenderSentCalculationId)
      .getOrElse { problemMessage -> throw CrdWebException(problemMessage, HttpStatus.NOT_FOUND) }
    val releaseDatesForSentCalculationId = releaseDates(nomisOffenderKeyDates)

    val detailsReleaseDates = calculationResultEnrichmentService.addDetailToCalculationDates(
      releaseDatesForSentCalculationId,
      null,
      null,
      null,
      nomisOffenderKeyDates,
      null,
    ).values.toList()

    val nomisReason =
      prisonService.getNOMISCalcReasons().find { it.code == nomisOffenderKeyDates.reasonCode }?.description
        ?: nomisOffenderKeyDates.reasonCode
    return NomisCalculationSummary(
      reason = nomisReason,
      calculatedAt = nomisOffenderKeyDates.calculatedAt,
      comment = nomisOffenderKeyDates.comment,
      releaseDates = detailsReleaseDates,
      calculatedByUsername = nomisOffenderKeyDates.calculatedByUserId,
      calculatedByDisplayName = nomisOffenderKeyDates.calculatedByUserId?.let { manageUsersApiClient.getUserByUsername(it)?.name } ?: nomisOffenderKeyDates.calculatedByUserId,
    )
  }

  private fun createASLEDIfWeCan(prisonerCalculation: OffenderKeyDates): ReleaseDate? = if (prisonerCalculation.sentenceExpiryDate != null && prisonerCalculation.licenceExpiryDate != null && prisonerCalculation.sentenceExpiryDate == prisonerCalculation.licenceExpiryDate) {
    ReleaseDate(prisonerCalculation.sentenceExpiryDate, ReleaseDateType.SLED)
  } else {
    null
  }

  fun releaseDates(prisonerCalculation: OffenderKeyDates): List<ReleaseDate> {
    val sled = createASLEDIfWeCan(prisonerCalculation)
    val dates = listOfNotNull(
      sled,
      if (sled != null) null else prisonerCalculation.sentenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.SED) },
      if (sled != null) null else prisonerCalculation.licenceExpiryDate?.let { ReleaseDate(it, ReleaseDateType.LED) },
      prisonerCalculation.conditionalReleaseDate?.let { ReleaseDate(it, ReleaseDateType.CRD) },
      prisonerCalculation.automaticReleaseDate?.let { ReleaseDate(it, ReleaseDateType.ARD) },
      prisonerCalculation.homeDetentionCurfewEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.HDCED) },
      prisonerCalculation.topupSupervisionExpiryDate?.let { ReleaseDate(it, ReleaseDateType.TUSED) },
      prisonerCalculation.postRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.PRRD) },
      prisonerCalculation.paroleEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.PED) },
      prisonerCalculation.releaseOnTemporaryLicenceDate?.let { ReleaseDate(it, ReleaseDateType.ROTL) },
      prisonerCalculation.earlyRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.ERSED) },
      prisonerCalculation.homeDetentionCurfewApprovedDate?.let { ReleaseDate(it, ReleaseDateType.HDCAD) },
      prisonerCalculation.midTermDate?.let { ReleaseDate(it, ReleaseDateType.MTD) },
      prisonerCalculation.earlyTermDate?.let { ReleaseDate(it, ReleaseDateType.ETD) },
      prisonerCalculation.lateTermDate?.let { ReleaseDate(it, ReleaseDateType.LTD) },
      prisonerCalculation.approvedParoleDate?.let { ReleaseDate(it, ReleaseDateType.APD) },
      prisonerCalculation.nonParoleDate?.let { ReleaseDate(it, ReleaseDateType.NPD) },
      prisonerCalculation.dtoPostRecallReleaseDate?.let { ReleaseDate(it, ReleaseDateType.DPRRD) },
      prisonerCalculation.tariffDate?.let { ReleaseDate(it, ReleaseDateType.Tariff) },
      prisonerCalculation.tariffExpiredRemovalSchemeEligibilityDate?.let { ReleaseDate(it, ReleaseDateType.TERSED) },
    )
    return dates
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
