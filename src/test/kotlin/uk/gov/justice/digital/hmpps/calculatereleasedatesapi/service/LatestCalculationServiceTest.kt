package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class LatestCalculationServiceTest {

  private val prisonService: PrisonService = mock()
  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService = mock()
  private val service = LatestCalculationService(prisonService, calculationRequestRepository, calculationResultEnrichmentService)
  private val prisonerId = "ABC123"
  private val bookingId = 123456L
  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    "John",
    "Smith",
    LocalDate.of(1970, 1, 1),
  )

  @Test
  fun `should return a problem if could not load prisoner details`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo("Prisoner (${prisonerId}) could not be found".left())
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner details`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.latestCalculationForPrisoner(prisonerId)
    }
  }

  @Test
  fun `should return a problem if could not load key dates from prison API`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo("Key dates for booking ($bookingId) could not be found".left())
  }

  @Test
  fun `should throw other unhandled exceptions loading prisoner key dates`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenThrow(WebClientResponseException(500, "Boom", null, null, null))

    assertThrows<WebClientResponseException>("Boom") {
      service.latestCalculationForPrisoner(prisonerId)
    }
  }

  @Test
  fun `if there are no CRDS calcs then return as NOMIS`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        null,
        CalculationSource.NOMIS,
        emptyMap(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS calculation if the comment doesn't contain the CRDS calc reference`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(comment = "Not this one"))
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        null,
        CalculationSource.NOMIS,
        emptyMap(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS calculation if the comment is null`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        null,
        CalculationSource.NOMIS,
        emptyMap(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS reason code for reason if it's set`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(reasonCode = "NEW"))
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        "NEW",
        CalculationSource.NOMIS,
        emptyMap(),
      ).right(),
    )
  }

  @Test
  fun `Should map all possible NOMIS dates`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        licenceExpiryDate = LocalDate.of(2025, 1, 2),
        paroleEligibilityDate = LocalDate.of(2025, 1, 3),
        homeDetentionCurfewEligibilityDate = LocalDate.of(2025, 1, 4),
        homeDetentionCurfewApprovedDate = LocalDate.of(2025, 1, 5),
        automaticReleaseDate = LocalDate.of(2025, 1, 6),
        conditionalReleaseDate = LocalDate.of(2025, 1, 7),
        nonParoleDate = LocalDate.of(2025, 1, 8),
        postRecallReleaseDate = LocalDate.of(2025, 1, 9),
        approvedParoleDate = LocalDate.of(2025, 1, 10),
        topupSupervisionExpiryDate = LocalDate.of(2025, 1, 11),
        earlyTermDate = LocalDate.of(2025, 1, 12),
        midTermDate = LocalDate.of(2025, 1, 13),
        lateTermDate = LocalDate.of(2025, 1, 14),
        tariffDate = LocalDate.of(2025, 1, 15),
        releaseOnTemporaryLicenceDate = LocalDate.of(2025, 1, 16),
        earlyRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 17),
        tariffExpiredRemovalSchemeEligibilityDate = LocalDate.of(2025, 1, 18),
        dtoPostRecallReleaseDate = LocalDate.of(2025, 1, 19),
        reasonCode = "NEW",
      ),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 3), ReleaseDateType.PED),
      ReleaseDate(LocalDate.of(2025, 1, 4), ReleaseDateType.HDCED),
      ReleaseDate(LocalDate.of(2025, 1, 5), ReleaseDateType.HDCAD),
      ReleaseDate(LocalDate.of(2025, 1, 6), ReleaseDateType.ARD),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
      ReleaseDate(LocalDate.of(2025, 1, 8), ReleaseDateType.NPD),
      ReleaseDate(LocalDate.of(2025, 1, 9), ReleaseDateType.PRRD),
      ReleaseDate(LocalDate.of(2025, 1, 10), ReleaseDateType.APD),
      ReleaseDate(LocalDate.of(2025, 1, 11), ReleaseDateType.TUSED),
      ReleaseDate(LocalDate.of(2025, 1, 12), ReleaseDateType.ETD),
      ReleaseDate(LocalDate.of(2025, 1, 13), ReleaseDateType.MTD),
      ReleaseDate(LocalDate.of(2025, 1, 14), ReleaseDateType.LTD),
      ReleaseDate(LocalDate.of(2025, 1, 15), ReleaseDateType.Tariff),
      ReleaseDate(LocalDate.of(2025, 1, 16), ReleaseDateType.ROTL),
      ReleaseDate(LocalDate.of(2025, 1, 17), ReleaseDateType.ERSED),
      ReleaseDate(LocalDate.of(2025, 1, 18), ReleaseDateType.TERSED),
      ReleaseDate(LocalDate.of(2025, 1, 19), ReleaseDateType.DPRRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates)

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        null,
        null,
        "NEW",
        CalculationSource.NOMIS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should map CRDS additional fields into the results if the CRDS calc ref appears in the commend`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        licenceExpiryDate = LocalDate.of(2025, 1, 2),
        conditionalReleaseDate = LocalDate.of(2025, 1, 7),
        reasonCode = "NEW",
        comment = "Some stuff and then the ref: $calculationReference",
      ),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(
        CalculationRequest(
          calculationReference = calculationReference,
          calculatedAt = calculatedAt,
          reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null),
        ),
      ),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates)
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        calculatedAt,
        null,
        "Some reason",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should lookup the location if there is one set on CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        comment = "Some stuff and then the ref: $calculationReference",
      ),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(CalculationRequest(calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC")),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates)
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        calculatedAt,
        "HMP ABC",
        null,
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should default to location code if it's not in agency lookup`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        comment = "Some stuff and then the ref: $calculationReference",
      ),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(CalculationRequest(calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "XYZ")),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates)
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        calculatedAt,
        "XYZ",
        null,
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  private fun toDetailedDates(dates: List<ReleaseDate>): Map<ReleaseDateType, DetailedDate> = dates.map { DetailedDate(it.type, it.type.description, it.date, emptyList()) }.associateBy { it.type }
}
