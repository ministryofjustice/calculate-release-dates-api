package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.right
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BreakdownMissingReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class LatestCalculationServiceTest {

  private val prisonService: PrisonService = mock()
  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val calculationResultEnrichmentService: CalculationResultEnrichmentService = mock()
  private val calculationBreakdownService: CalculationBreakdownService = mock()
  private val prisonApiDataMapper: PrisonApiDataMapper = mock()
  private val service = LatestCalculationService(prisonService, calculationRequestRepository, calculationResultEnrichmentService, calculationBreakdownService, prisonApiDataMapper)
  private val objectMapper = TestUtil.objectMapper()
  private val prisonerId = "ABC123"
  private val bookingId = 123456L
  private val prisonerDetails = PrisonerDetails(
    bookingId,
    prisonerId,
    "John",
    "Smith",
    LocalDate.of(1970, 1, 1),
  )
  private val now = LocalDateTime.now()

  @Test
  fun `should return a problem if could not load prisoner details`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenThrow(WebClientResponseException(404, "Not found", null, null, null))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo("Prisoner ($prisonerId) could not be found".left())
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
    val expectedError = "Bang!"
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(expectedError.left())

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(expectedError.left())
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
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(reasonCode = "NEW", calculatedAt = now).right())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        "New Sentence",
        CalculationSource.NOMIS,
        emptyList(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS calculation if the comment doesn't contain the CRDS calc reference`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(reasonCode = "NEW", calculatedAt = now, comment = "Not this one").right())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        "New Sentence",
        CalculationSource.NOMIS,
        emptyList(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS calculation if the comment is null`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(reasonCode = "NEW", calculatedAt = now).right())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        "New Sentence",
        CalculationSource.NOMIS,
        emptyList(),
      ).right(),
    )
  }

  @Test
  fun `Should use the NOMIS reason code for reason if we can't find the looked up code`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(OffenderKeyDates(reasonCode = "FOO", calculatedAt = now).right())
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(CalculationRequest(calculationReference = UUID.randomUUID())))

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        "FOO",
        CalculationSource.NOMIS,
        emptyList(),
      ).right(),
    )
  }

  @Test
  fun `Should map all possible NOMIS dates`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getNOMISCalcReasons()).thenReturn(listOf(NomisCalculationReason("NEW", "New Sentence")))
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
        calculatedAt = now,
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
      ReleaseDate(LocalDate.of(2025, 1, 2), ReleaseDateType.LED),
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
      ReleaseDate(LocalDate.of(2025, 1, 6), ReleaseDateType.ARD),
      ReleaseDate(LocalDate.of(2025, 1, 4), ReleaseDateType.HDCED),
      ReleaseDate(LocalDate.of(2025, 1, 11), ReleaseDateType.TUSED),
      ReleaseDate(LocalDate.of(2025, 1, 9), ReleaseDateType.PRRD),
      ReleaseDate(LocalDate.of(2025, 1, 3), ReleaseDateType.PED),
      ReleaseDate(LocalDate.of(2025, 1, 16), ReleaseDateType.ROTL),
      ReleaseDate(LocalDate.of(2025, 1, 17), ReleaseDateType.ERSED),
      ReleaseDate(LocalDate.of(2025, 1, 5), ReleaseDateType.HDCAD),
      ReleaseDate(LocalDate.of(2025, 1, 13), ReleaseDateType.MTD),
      ReleaseDate(LocalDate.of(2025, 1, 12), ReleaseDateType.ETD),
      ReleaseDate(LocalDate.of(2025, 1, 14), ReleaseDateType.LTD),
      ReleaseDate(LocalDate.of(2025, 1, 10), ReleaseDateType.APD),
      ReleaseDate(LocalDate.of(2025, 1, 8), ReleaseDateType.NPD),
      ReleaseDate(LocalDate.of(2025, 1, 19), ReleaseDateType.DPRRD),
      ReleaseDate(LocalDate.of(2025, 1, 15), ReleaseDateType.Tariff),
      ReleaseDate(LocalDate.of(2025, 1, 18), ReleaseDateType.TERSED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })

    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        now,
        null,
        null,
        "New Sentence",
        CalculationSource.NOMIS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should replace SED and LED with SLED for NOMIS if SED and LED are the same`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        licenceExpiryDate = LocalDate.of(2025, 1, 1),
        reasonCode = "NEW",
        calculatedAt = now,
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SLED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    val generatedDates = service.latestCalculationForPrisoner(prisonerId).map { it.dates }.getOrNull()
    assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SLED && it.date == LocalDate.of(2025, 1, 1) }
    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
  }

  @Test
  fun `Should not create SLED for NOMIS if SED and LED are both absent`() {
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = now,
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.empty())

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SLED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })

    assertThat(service.latestCalculationForPrisoner(prisonerId).map { it.dates }.getOrNull()).noneMatch { it.type == ReleaseDateType.SLED }
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
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(
        CalculationRequest(
          id = 654321,
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
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        null,
        "Some reason",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should default to Not entered if reason for calc was not enabled on CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        conditionalReleaseDate = LocalDate.of(2025, 1, 7),
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(
        CalculationRequest(
          id = 654321,
          calculationReference = calculationReference,
          calculatedAt = calculatedAt,
          reasonForCalculation = null,
        ),
      ),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 7), ReleaseDateType.CRD),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(service.latestCalculationForPrisoner(prisonerId).getOrNull()!!.reason).isEqualTo("Not entered")
  }

  @Test
  fun `Should create SLED for CRDS if LED and SED are the same`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
        licenceExpiryDate = LocalDate.of(2025, 1, 1),
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(
        CalculationRequest(
          id = 654321,
          calculationReference = calculationReference,
          calculatedAt = calculatedAt,
          reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null),
        ),
      ),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SLED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())

    val generatedDates = service.latestCalculationForPrisoner(prisonerId).map { it.dates }.getOrNull()
    assertThat(generatedDates).anyMatch { it.type == ReleaseDateType.SLED && it.date == LocalDate.of(2025, 1, 1) }
    assertThat(generatedDates).noneMatch { it.type == ReleaseDateType.SED || it.type == ReleaseDateType.LED }
  }

  @Test
  fun `Should not create SLED for CRDS if SED and LED are both absent`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()
    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(
        CalculationRequest(
          id = 654321,
          calculationReference = calculationReference,
          calculatedAt = calculatedAt,
          reasonForCalculation = CalculationReason(0, false, false, "Some reason", false, null, null, null),
        ),
      ),
    )
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SLED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })

    assertThat(service.latestCalculationForPrisoner(prisonerId).map { it.dates }.getOrNull()).noneMatch { it.type == ReleaseDateType.SLED }
  }

  @Test
  fun `Should lookup the location if there is one set on CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC")),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        "HMP ABC",
        "Not entered",
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
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      ).right(),
    )
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(
      Optional.of(CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "XYZ")),
    )

    val dates = listOf(
      ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED),
    )
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, null)).thenReturn(detailedDates.associateBy { it.type })
    whenever(calculationBreakdownService.getBreakdownSafely(any())).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        "XYZ",
        "Not entered",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should pass breakdown and sentences and offences for CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      ).right(),
    )
    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC", sentenceAndOffences = objectToJson(listOf(someSentence), objectMapper))
    val expectedBreakdown = CalculationBreakdown(emptyList(), null)
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(expectedBreakdown.right())
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(someSentence))

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, listOf(someSentence), expectedBreakdown)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        "HMP ABC",
        "Not entered",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should not blow up if breakdown can't be generated for CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      ).right(),
    )
    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC", sentenceAndOffences = objectToJson(listOf(someSentence), objectMapper))
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(BreakdownMissingReason.UNSUPPORTED_CALCULATION_BREAKDOWN.left())
    whenever(prisonApiDataMapper.mapSentencesAndOffences(calculationRequest)).thenReturn(listOf(someSentence))

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, listOf(someSentence), null)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        "HMP ABC",
        "Not entered",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
  }

  @Test
  fun `Should not blow up if sentences and offences are missing for CRDS`() {
    val calculationReference = UUID.randomUUID()
    val calculatedAt = LocalDateTime.now()

    whenever(prisonService.getOffenderDetail(prisonerId)).thenReturn(prisonerDetails)
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf(Agency("ABC", "HMP ABC")))
    whenever(prisonService.getOffenderKeyDates(bookingId)).thenReturn(
      OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = calculatedAt,
        comment = "Some stuff and then the ref: $calculationReference",
        sentenceExpiryDate = LocalDate.of(2025, 1, 1),
      ).right(),
    )
    val calculationRequest = CalculationRequest(id = 654321, calculationReference = calculationReference, calculatedAt = calculatedAt, prisonerLocation = "ABC", sentenceAndOffences = null)
    val expectedBreakdown = CalculationBreakdown(emptyList(), null)
    whenever(calculationRequestRepository.findLatestConfirmedCalculationForPrisoner(prisonerId)).thenReturn(Optional.of(calculationRequest))
    whenever(calculationBreakdownService.getBreakdownSafely(calculationRequest)).thenReturn(expectedBreakdown.right())

    val dates = listOf(ReleaseDate(LocalDate.of(2025, 1, 1), ReleaseDateType.SED))
    val detailedDates = toDetailedDates(dates)
    whenever(calculationResultEnrichmentService.addDetailToCalculationDates(dates, null, expectedBreakdown)).thenReturn(detailedDates.associateBy { it.type })
    assertThat(service.latestCalculationForPrisoner(prisonerId)).isEqualTo(
      LatestCalculation(
        prisonerId,
        bookingId,
        calculatedAt,
        654321,
        "HMP ABC",
        "Not entered",
        CalculationSource.CRDS,
        detailedDates,
      ).right(),
    )
    verify(prisonApiDataMapper, never()).mapSentencesAndOffences(calculationRequest)
  }

  private val someSentence = SentenceAndOffences(
    bookingId = 1L,
    sentenceSequence = 3,
    lineSequence = 2,
    caseSequence = 1,
    sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
    terms = listOf(
      SentenceTerms(years = 8),
    ),
    sentenceStatus = "IMP",
    sentenceCategory = "CAT",
    sentenceCalculationType = SentenceCalculationType.ADIMP.name,
    sentenceTypeDescription = "ADMIP",
    offences = listOf(OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A"))),
  )

  private fun toDetailedDates(dates: List<ReleaseDate>): List<DetailedDate> = dates.map { DetailedDate(it.type, it.type.description, it.date, emptyList()) }
}
