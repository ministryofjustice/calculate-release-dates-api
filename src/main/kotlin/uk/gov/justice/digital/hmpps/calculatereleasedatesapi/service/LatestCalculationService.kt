package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageusersapi.model.PrisonUserBasicDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerCalculationOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.util.Optional
import kotlin.math.max

@Component
class LatestCalculationService(
  private val prisonService: PrisonService,
  private val offenderKeyDatesService: OffenderKeyDatesService,
  private val calculationRequestRepository: CalculationRequestRepository,
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService,
  private val calculationBreakdownService: CalculationBreakdownService,
  private val calculationOutcomeHistoricOverrideRepository: CalculationOutcomeHistoricOverrideRepository,
  private val sourceDataMapper: SourceDataMapper,
  private val manageUsersApiClient: ManageUsersApiClient,
) {

  @Transactional(readOnly = true)
  fun latestCalculationForPrisoner(prisonerId: String): Either<String, LatestCalculation> = getLatestBookingFromPrisoner(prisonerId)
    .flatMap { bookingId -> prisonService.getOffenderKeyDates(bookingId).map { bookingId to it } }
    .map { (bookingId, prisonerCalculation) ->
      val latestCrdsCalc = calculationRequestRepository.findFirstByPrisonerIdAndCalculationStatusOrderByCalculatedAtDesc(prisonerId)
      val sameCalc = isSameCalc(prisonerCalculation.comment, latestCrdsCalc)
      val requiredUsersNames = latestCrdsCalc
        .map { calc -> listOfNotNull(calc.calculatedByUsername) + calc.secondChecks.map { it.checkedByUsername } }
        .orElse(listOf())
        .plus(prisonerCalculation.calculatedByUserId)
        .map { it.uppercase() }.toSet()
      val prisonDetails = if (sameCalc) prisonService.getAgenciesByType("INST") else emptyList()
      val nomisReasons = if (!sameCalc) prisonService.getNOMISCalcReasons() else emptyList()
      val userDetails = manageUsersApiClient.getUsersByUsernames(requiredUsersNames)
      val metaData = LatestCalcMetaData(prisonDetails, userDetails ?: emptyMap(), nomisReasons)

      if (sameCalc) {
        val calculationRequest = latestCrdsCalc.get()
        val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
        val breakdown = calculationBreakdownService.getBreakdownSafely(calculationRequest).getOrNull()
        toLatestDpsCalculation(
          calculationRequest.id(),
          prisonerId,
          bookingId,
          prisonerCalculation,
          calculationRequest,
          metaData,
          sentenceAndOffences,
          breakdown,
        )
      } else {
        toLatestNomisCalculation(
          prisonerId,
          bookingId,
          prisonerCalculation,
          metaData,
        )
      }
    }

  @Transactional(readOnly = true)
  fun latestCalculationOverviewForPrisoner(prisonerId: String, numberOfRecentCalculations: Int): Either<String, PrisonerCalculationOverview> = getLatestBookingFromPrisoner(prisonerId)
    .map { bookingId -> prisonService.getOffenderKeyDates(bookingId).fold({ bookingId to null }, { bookingId to it }) }
    .map { (bookingId, latestNomisCalculation) ->

      // retrieve at least one NOMIS and CRDS calculation to ensure latest calc and metadata is loaded appropriately even if no summaries requested.
      val allNomisCalculations = prisonService.getCalculationsForAPrisonerId(prisonerId)
      val crdsCalculations = calculationRequestRepository
        .findAllByPrisonerIdAndCalculationStatus(prisonerId, CONFIRMED.name)
        .sortedByDescending { it.calculatedAt }
        .take(max(numberOfRecentCalculations, 1))
      val nomisCalculationsToCrdsCalculations = allNomisCalculations
        .sortedByDescending { it.calculationDate }
        .take(max(numberOfRecentCalculations, 1))
        .map { nomisCalculation -> nomisCalculation to crdsCalculations.find { crdsCalculation -> isSameCalc(nomisCalculation.commentText, Optional.of(crdsCalculation)) } }

      val latestCrdsCalc = Optional.ofNullable(crdsCalculations.maxByOrNull { it.calculatedAt })

      val requiredUsersNames = nomisCalculationsToCrdsCalculations
        .flatMap { (nomisCalc, crdsCalc) -> listOfNotNull(nomisCalc.calculatedByUserId, crdsCalc?.calculatedByUsername) + (crdsCalc?.secondChecks ?: emptyList()).map { it.checkedByUsername } }
        .map { it.uppercase() }.toSet()

      val anyCrdsCalcs = nomisCalculationsToCrdsCalculations.any { (_, crdsCalc) -> crdsCalc != null }
      val anyNomisCalcs = nomisCalculationsToCrdsCalculations.any { (_, crdsCalc) -> crdsCalc == null }

      val prisonDetails = if (anyCrdsCalcs) prisonService.getAgenciesByType("INST") else emptyList()
      val nomisReasons = if (anyNomisCalcs) prisonService.getNOMISCalcReasons() else emptyList()

      val userDetails = manageUsersApiClient.getUsersByUsernames(requiredUsersNames)
      val metaData = LatestCalcMetaData(prisonDetails, userDetails ?: emptyMap(), nomisReasons)

      val sentencesAndOffences = prisonService.getSentencesAndOffences(bookingId)
      val hasIndeterminateSentences = sentencesAndOffences.any { SentenceCalculationType.isIndeterminate(it.sentenceCalculationType) }

      val latestCalculation = if (latestNomisCalculation != null && isSameCalc(latestNomisCalculation.comment, latestCrdsCalc)) {
        val calculationRequest = latestCrdsCalc.get()
        val sentenceAndOffences = calculationRequest.sentenceAndOffences?.let { sourceDataMapper.mapSentencesAndOffences(calculationRequest) }
        val breakdown = calculationBreakdownService.getBreakdownSafely(calculationRequest).getOrNull()
        toLatestDpsCalculation(
          calculationRequest.id(),
          prisonerId,
          bookingId,
          latestNomisCalculation,
          calculationRequest,
          metaData,
          sentenceAndOffences,
          breakdown,
        )
      } else if (latestNomisCalculation != null) {
        toLatestNomisCalculation(
          prisonerId,
          bookingId,
          latestNomisCalculation,
          metaData,
        )
      } else {
        null
      }
      val historicCalculationSummaries = nomisCalculationsToCrdsCalculations.map { (nomisCalc, crdsCalc) ->
        if (crdsCalc != null) {
          val location = crdsCalc.prisonerLocation
            ?.let { metaData.prisons.firstOrNull { it.agencyId == crdsCalc.prisonerLocation }?.description ?: crdsCalc.prisonerLocation }
          HistoricCalculationSummary(
            calculationDate = crdsCalc.calculatedAt,
            calculationSource = CalculationSource.CRDS,
            calculationType = crdsCalc.calculationType,
            crdsCalculationId = crdsCalc.id(),
            nomisCalculationId = nomisCalc.offenderSentCalculationId,
            reasonDescription = crdsCalc.reasonForCalculation?.displayName ?: "Not entered",
            reasonFurtherDetail = crdsCalc.otherReasonForCalculation,
            genuineOverrideReasonDescription = crdsCalc.genuineOverrideReason?.description,
            calculatedByDisplayName = formatUsersName(metaData.usersDetails, crdsCalc.calculatedByUsername),
            establishmentCalculatedAtDescription = location,
          )
        } else {
          val nomisReason = metaData.nomisReasons.find { it.code == nomisCalc.calculationReason }?.description ?: nomisCalc.calculationReason
          HistoricCalculationSummary(
            calculationDate = nomisCalc.calculationDate,
            calculationSource = CalculationSource.NOMIS,
            calculationType = null,
            crdsCalculationId = null,
            nomisCalculationId = nomisCalc.offenderSentCalculationId,
            reasonDescription = nomisReason,
            reasonFurtherDetail = null,
            genuineOverrideReasonDescription = null,
            calculatedByDisplayName = formatUsersName(metaData.usersDetails, nomisCalc.calculatedByUserId),
            establishmentCalculatedAtDescription = nomisCalc.agencyDescription,
          )
        }
      }
      PrisonerCalculationOverview(
        latestCalculation = latestCalculation,
        recentCalculations = historicCalculationSummaries.take(numberOfRecentCalculations),
        totalCalculationCount = allNomisCalculations.size,
        numberOfSentences = sentencesAndOffences.size,
        hasIndeterminateSentences = hasIndeterminateSentences,
      )
    }

  private fun isSameCalc(nomisComment: String?, latestCrdsCalc: Optional<CalculationRequest>): Boolean = when {
    nomisComment == null -> false
    latestCrdsCalc.map { it.calculationReference }.orElse(null).let { calculationReference -> calculationReference != null && nomisComment.contains(calculationReference.toString()) } -> true
    else -> false
  }

  private fun getLatestBookingFromPrisoner(prisonerId: String): Either<String, Long> = try {
    prisonService.getOffenderDetail(prisonerId).bookingId.right()
  } catch (e: WebClientResponseException) {
    if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
      "Prisoner ($prisonerId) could not be found".left()
    } else {
      throw e
    }
  }

  private fun toLatestDpsCalculation(
    calculationRequestId: Long,
    prisonerId: String,
    bookingId: Long,
    prisonerCalculation: OffenderKeyDates,
    calculationRequest: CalculationRequest,
    metaData: LatestCalcMetaData,
    sentenceAndOffences: List<SentenceAndOffenceWithReleaseArrangements>?,
    breakdown: CalculationBreakdown?,
  ): LatestCalculation {
    val secondCheck = calculationRequest.secondChecks.maxByOrNull { it.checkedAt }

    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    val historicSledOverride = calculationOutcomeHistoricOverrideRepository.findByCalculationRequestId(calculationRequestId)
    val location = calculationRequest.prisonerLocation
      ?.let { metaData.prisons.firstOrNull { it.agencyId == calculationRequest.prisonerLocation }?.description ?: calculationRequest.prisonerLocation }

    return LatestCalculation(
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculatedAt = prisonerCalculation.calculatedAt,
      calculationRequestId = calculationRequestId,
      establishment = location,
      reason = calculationRequest.reasonForCalculation?.displayName ?: "Not entered",
      reasonFurtherDetail = calculationRequest.otherReasonForCalculation,
      genuineOverrideReasonDescription = calculationRequest.genuineOverrideReason?.description,
      source = CalculationSource.CRDS,
      calculatedByUsername = calculationRequest.calculatedByUsername,
      checkedByUsername = secondCheck?.checkedByUsername,
      checkedAt = secondCheck?.checkedAt,
      calculatedByDisplayName = formatUsersName(metaData.usersDetails, calculationRequest.calculatedByUsername),
      checkedByDisplayName = secondCheck?.checkedByUsername?.let { username -> formatUsersName(metaData.usersDetails, username) },
      calculationType = calculationRequest.calculationType.name,
      dates = calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        sentenceAndOffences,
        breakdown,
        calculationRequest.historicalTusedSource,
        null,
        historicSledOverride,
      ).values.toList(),
    )
  }

  private fun toLatestNomisCalculation(
    prisonerId: String,
    bookingId: Long,
    prisonerCalculation: OffenderKeyDates,
    metaData: LatestCalcMetaData,
  ): LatestCalculation {
    val dates = offenderKeyDatesService.releaseDates(prisonerCalculation)
    val nomisReason = metaData.nomisReasons.find { it.code == prisonerCalculation.reasonCode }?.description ?: prisonerCalculation.reasonCode
    return LatestCalculation(
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculatedAt = prisonerCalculation.calculatedAt,
      calculationRequestId = null,
      establishment = null,
      reason = nomisReason,
      reasonFurtherDetail = null,
      genuineOverrideReasonDescription = null,
      source = CalculationSource.NOMIS,
      calculatedByUsername = prisonerCalculation.calculatedByUserId,
      calculatedByDisplayName = formatUsersName(metaData.usersDetails, prisonerCalculation.calculatedByUserId),
      calculationType = "Unknown",
      checkedAt = null,
      checkedByUsername = null,
      checkedByDisplayName = null,
      dates = calculationResultEnrichmentService.addDetailToCalculationDates(
        dates,
        null,
        null,
        null,
        prisonerCalculation,
        null,
      ).values.toList(),
    )
  }

  private fun formatUsersName(
    usersDetails: Map<String, PrisonUserBasicDetails>,
    username: String,
  ): String {
    val userDetails = usersDetails[username.uppercase()]
    return listOfNotNull(userDetails?.firstName, userDetails?.lastName).joinToString(" ").ifBlank { username }
  }

  private data class LatestCalcMetaData(
    val prisons: List<Agency>,
    val usersDetails: Map<String, PrisonUserBasicDetails>,
    val nomisReasons: List<NomisCalculationReason>,
  )
}
