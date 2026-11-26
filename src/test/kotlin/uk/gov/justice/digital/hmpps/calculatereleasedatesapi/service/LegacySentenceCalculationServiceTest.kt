package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.info.BuildProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacyCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aFullyCompletedLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aMinimalLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacyCalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.Properties
import java.util.UUID
import java.util.stream.Stream

class LegacySentenceCalculationServiceTest {

  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val legacySentenceCalculationRepository: LegacySentenceCalculationRepository = mock()
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository = mock()
  private val buildProperties: BuildProperties = BuildProperties(Properties().apply { setProperty("version", "somever") })
  private val calculationReasonRepository: CalculationReasonRepository = mock()
  private val calculationOutcomeRepository: CalculationOutcomeRepository = mock()
  private val legacyCalculationReasonRepository: LegacyCalculationReasonRepository = mock()

  private val service = LegacySentenceCalculationService(
    calculationRequestRepository,
    legacySentenceCalculationRepository,
    approvedDatesSubmissionRepository,
    buildProperties,
    calculationReasonRepository,
    legacyCalculationReasonRepository,
    calculationOutcomeRepository,
  )
  private val newReason = CalculationReason(
    1,
    isActive = true,
    isOther = false,
    isBulk = false,
    displayName = "Reason 1",
    nomisReason = "NEW",
    nomisComment = "NOMIS_COMMENT",
    displayRank = 10,
    useForApprovedDates = false,
    eligibleForPreviouslyRecordedSled = false,
    requiresFurtherDetail = false,
    furtherDetailDescription = null,
    isSecondCheck = false,
  )
  private val otherReason = CalculationReason(
    3,
    isActive = true,
    isOther = true,
    isBulk = false,
    displayName = "Other",
    nomisReason = "OTHER",
    nomisComment = "NOMIS_COMMENT",
    displayRank = 20,
    useForApprovedDates = false,
    eligibleForPreviouslyRecordedSled = false,
    requiresFurtherDetail = false,
    furtherDetailDescription = null,
    isSecondCheck = false,
  )

  @BeforeEach
  fun setUp() {
    whenever(calculationReasonRepository.findTopByIsOtherTrue()).thenReturn(Optional.of(otherReason))
    whenever(legacyCalculationReasonRepository.findByCode("NEW")).thenReturn(LegacyCalculationReason(1, "NEW", "New from NOMIS", newReason))
    whenever(legacyCalculationReasonRepository.findByCode("UPDATE")).thenReturn(LegacyCalculationReason(1, "UPDATE", "Update from NOMIS", null))
  }

  @Test
  fun `migration maps all fields from request to entity when there is no mapping to a CRDS calculation and creates a CRDS calculation for the latest calculation`() {
    val aMinimalRequest = aMinimalLegacySentenceCalculationDto().copy(calculationDate = LocalDateTime.now().minusDays(1))
    val aFullyCompletedRequest = aFullyCompletedLegacySentenceCalculationDto().copy(calculationDate = LocalDateTime.now())
    val calculationReferenceInNewCalc = UUID.randomUUID()

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequest, null)
    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequest, CalculationRequest(id = 100000, calculationReference = calculationReferenceInNewCalc))

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequest, aFullyCompletedRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, null),
      LegacySentenceCalculationToCrdsMappingResult(2, 100000),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
    val calculationRequest = argumentCaptor<CalculationRequest>()
    verify(calculationRequestRepository).saveAndFlush(calculationRequest.capture())
    assertThat(calculationRequest.firstValue.reasonForCalculation).isEqualTo(newReason)
  }

  @Test
  fun `migration finds a UUID within a request and links it to the calculation`() {
    val calculationReference = UUID.randomUUID()
    val calculationRequestId = 123456L

    val aFullyCompletedRequestWithAMatchingCalculationRequest = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReference", calculationDate = LocalDateTime.now())
    val aMinimalRequestWithNoMatch = aMinimalLegacySentenceCalculationDto().copy(calculationDate = LocalDateTime.now().minusDays(1))

    val calculationRequest = CalculationRequest(id = calculationRequestId, calculationReference = calculationReference, prisonerId = aFullyCompletedRequestWithAMatchingCalculationRequest.prisonerId)

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequestWithNoMatch, null)
    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequestWithAMatchingCalculationRequest, calculationRequest)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.findByCalculationReference(calculationReference)).thenReturn(Optional.of(calculationRequest))

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequestWithNoMatch, aFullyCompletedRequestWithAMatchingCalculationRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, null),
      LegacySentenceCalculationToCrdsMappingResult(2, calculationRequestId),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
    verify(calculationRequestRepository, never()).setPrisonerIdForCalculationRequest(any(), any(), any(), any(), any())
    verify(approvedDatesSubmissionRepository, never()).setPrisonerIdForCalculationRequest(any(), any(), any(), any(), any())
  }

  @Test
  fun `migration finds a calculation but it's for a different prisoner so we update the prisoner id on the calc and approved dates submissions`() {
    val calculationReference = UUID.randomUUID()
    val calculationRequestId = 123456L

    val aFullyCompletedRequestWithAMatchingCalculationRequest = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReference")

    val calculationRequestForADifferentPrisonerId = CalculationRequest(id = calculationRequestId, calculationReference = calculationReference, prisonerId = "Someone Else")

    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequestWithAMatchingCalculationRequest, calculationRequestForADifferentPrisonerId)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenReturn(expectedFullyCompleteEntity)
    whenever(calculationRequestRepository.findByCalculationReference(calculationReference)).thenReturn(Optional.of(calculationRequestForADifferentPrisonerId))

    val request = LegacySentenceCalculationMigrationRequest(listOf(aFullyCompletedRequestWithAMatchingCalculationRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(2, calculationRequestId),
        ),
      ),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
    verify(calculationRequestRepository).setPrisonerIdForCalculationRequest(eq(calculationRequestId), eq("Someone Else"), eq("AB1234C"), eq(PrisonerModifiedReason.MIGRATION), any())
    verify(approvedDatesSubmissionRepository).setPrisonerIdForCalculationRequest(eq(calculationRequestId), eq("Someone Else"), eq("AB1234C"), eq(PrisonerModifiedReason.MIGRATION), any())
  }

  @Test
  fun `migration handles being unable to find the calculation request gracefully`() {
    val calculationReferenceInComment = UUID.randomUUID()
    val calculationReferenceInNewCalc = UUID.randomUUID()

    val aCalcWithAUUID = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReferenceInComment")

    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aCalcWithAUUID, CalculationRequest(id = 100000, calculationReference = calculationReferenceInNewCalc))

    whenever(calculationRequestRepository.findByCalculationReference(calculationReferenceInComment)).thenReturn(Optional.empty())
    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation -> invocation.getArgument<LegacySentenceCalculation>(0) }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aCalcWithAUUID))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(2, 100000),
        ),
      ),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
  }

  @Test
  fun `migration blows up if we find more than one UUID in the comment`() {
    val aCalcWithAUUID = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: ${UUID.randomUUID()} and another UUID: ${UUID.randomUUID()}")

    val request = LegacySentenceCalculationMigrationRequest(listOf(aCalcWithAUUID))

    val exception = assertThrows<IllegalArgumentException> {
      service.migrate("AB1234C", request)
    }
    assertThat(exception.message).isEqualTo("Found 2 UUIDs in the comment for NOMIS calculation 2")

    verify(legacySentenceCalculationRepository, never()).saveAndFlush(any<LegacySentenceCalculation>())
  }

  @Test
  fun `migration can create a new calculation request with no dates`() {
    val aMinimalRequest = aMinimalLegacySentenceCalculationDto()
    val calculationReferenceInNewCalc = UUID.randomUUID()

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequest, CalculationRequest(id = 100000, calculationReference = calculationReferenceInNewCalc))

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, 100000),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(calculationOutcomeRepository).saveAndFlush(CalculationOutcome(null, 100000L, null, ReleaseDateType.None.name))

    val calculationRequest = argumentCaptor<CalculationRequest>()
    verify(calculationRequestRepository).saveAndFlush(calculationRequest.capture())
    assertThat(calculationRequest.firstValue.reasonForCalculation).isEqualTo(newReason)
    assertThat(calculationRequest.firstValue.otherReasonForCalculation).isNull()
  }

  @Test
  fun `migration can create a new calculation request with no matching CRDS reason`() {
    val aMinimalRequest = aMinimalLegacySentenceCalculationDto().copy(sedCalculatedDate = LocalDate.now(), calculationReasonCode = "UPDATE")
    val calculationReferenceInNewCalc = UUID.randomUUID()

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, 100000),
    )

    val calculationRequest = argumentCaptor<CalculationRequest>()
    verify(calculationRequestRepository).saveAndFlush(calculationRequest.capture())
    assertThat(calculationRequest.firstValue.reasonForCalculation).isEqualTo(otherReason)
    assertThat(calculationRequest.firstValue.otherReasonForCalculation).isEqualTo("Update from NOMIS")
  }

  @Test
  fun `migration can create a new calculation request with no matching NOMIS reason`() {
    val aMinimalRequest = aMinimalLegacySentenceCalculationDto().copy(sedCalculatedDate = LocalDate.now(), calculationReasonCode = "blurg")
    val calculationReferenceInNewCalc = UUID.randomUUID()

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, 100000),
    )

    val calculationRequest = argumentCaptor<CalculationRequest>()
    verify(calculationRequestRepository).saveAndFlush(calculationRequest.capture())
    assertThat(calculationRequest.firstValue.reasonForCalculation).isEqualTo(otherReason)
    assertThat(calculationRequest.firstValue.otherReasonForCalculation).isEqualTo("blurg")
  }

  @ParameterizedTest
  @MethodSource("datesWithOverrideArguments")
  fun `selects correct date for each date type with or without override`(expectedDateType: ReleaseDateType, dto: LegacySentenceCalculationDto, expectedDate: LocalDate) {
    val calculationReferenceInNewCalc = UUID.randomUUID()

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(dto, CalculationRequest(id = 100000, calculationReference = calculationReferenceInNewCalc))

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      invocation.getArgument<LegacySentenceCalculation>(0)
    }
    whenever(calculationRequestRepository.saveAndFlush(any<CalculationRequest>())).thenAnswer { invocation -> invocation.getArgument<CalculationRequest>(0).copy(id = 100000, calculationReference = calculationReferenceInNewCalc) }
    whenever(calculationOutcomeRepository.saveAndFlush(any<CalculationOutcome>())).thenAnswer { invocation -> invocation.getArgument<CalculationOutcome>(0) }

    val request = LegacySentenceCalculationMigrationRequest(listOf(dto))

    val response = service.migrate("AB1234C", request)

    assertThat(response.mappingResults).containsExactlyInAnyOrder(
      LegacySentenceCalculationToCrdsMappingResult(1, 100000),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(calculationOutcomeRepository).saveAndFlush(CalculationOutcome(null, 100000L, expectedDate, expectedDateType.name))
  }

  companion object {
    @JvmStatic
    private fun datesWithOverrideArguments(): Stream<Arguments> {
      val minimal = aMinimalLegacySentenceCalculationDto()
      return Stream.of(
        // both and should select override
        Arguments.of(ReleaseDateType.HDCED, minimal.copy(hdcedCalculatedDate = LocalDate.of(2000, 1, 1), hdcedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.HDCAD, minimal.copy(hdcadCalculatedDate = LocalDate.of(2000, 1, 1), hdcadOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.ETD, minimal.copy(etdCalculatedDate = LocalDate.of(2000, 1, 1), etdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.MTD, minimal.copy(mtdCalculatedDate = LocalDate.of(2000, 1, 1), mtdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.LTD, minimal.copy(ltdCalculatedDate = LocalDate.of(2000, 1, 1), ltdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.ARD, minimal.copy(ardCalculatedDate = LocalDate.of(2000, 1, 1), ardOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.CRD, minimal.copy(crdCalculatedDate = LocalDate.of(2000, 1, 1), crdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.PED, minimal.copy(pedCalculatedDate = LocalDate.of(2000, 1, 1), pedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.APD, minimal.copy(apdCalculatedDate = LocalDate.of(2000, 1, 1), apdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.NPD, minimal.copy(npdCalculatedDate = LocalDate.of(2000, 1, 1), npdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.LED, minimal.copy(ledCalculatedDate = LocalDate.of(2000, 1, 1), ledOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.SED, minimal.copy(sedCalculatedDate = LocalDate.of(2000, 1, 1), sedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.PRRD, minimal.copy(prrdCalculatedDate = LocalDate.of(2000, 1, 1), prrdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.Tariff, minimal.copy(tariffCalculatedDate = LocalDate.of(2000, 1, 1), tariffOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.DPRRD, minimal.copy(dprrdCalculatedDate = LocalDate.of(2000, 1, 1), dprrdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.TUSED, minimal.copy(tusedCalculatedDate = LocalDate.of(2000, 1, 1), tusedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        // only override
        Arguments.of(ReleaseDateType.HDCED, minimal.copy(hdcedCalculatedDate = null, hdcedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.HDCAD, minimal.copy(hdcadCalculatedDate = null, hdcadOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.ETD, minimal.copy(etdCalculatedDate = null, etdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.MTD, minimal.copy(mtdCalculatedDate = null, mtdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.LTD, minimal.copy(ltdCalculatedDate = null, ltdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.ARD, minimal.copy(ardCalculatedDate = null, ardOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.CRD, minimal.copy(crdCalculatedDate = null, crdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.PED, minimal.copy(pedCalculatedDate = null, pedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.APD, minimal.copy(apdCalculatedDate = null, apdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.NPD, minimal.copy(npdCalculatedDate = null, npdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.LED, minimal.copy(ledCalculatedDate = null, ledOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.SED, minimal.copy(sedCalculatedDate = null, sedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.PRRD, minimal.copy(prrdCalculatedDate = null, prrdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.Tariff, minimal.copy(tariffCalculatedDate = null, tariffOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.DPRRD, minimal.copy(dprrdCalculatedDate = null, dprrdOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.TUSED, minimal.copy(tusedCalculatedDate = null, tusedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        // only calc
        Arguments.of(ReleaseDateType.HDCED, minimal.copy(hdcedCalculatedDate = LocalDate.of(2000, 1, 1), hdcedOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.HDCAD, minimal.copy(hdcadCalculatedDate = LocalDate.of(2000, 1, 1), hdcadOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.ETD, minimal.copy(etdCalculatedDate = LocalDate.of(2000, 1, 1), etdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.MTD, minimal.copy(mtdCalculatedDate = LocalDate.of(2000, 1, 1), mtdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.LTD, minimal.copy(ltdCalculatedDate = LocalDate.of(2000, 1, 1), ltdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.ARD, minimal.copy(ardCalculatedDate = LocalDate.of(2000, 1, 1), ardOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.CRD, minimal.copy(crdCalculatedDate = LocalDate.of(2000, 1, 1), crdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.PED, minimal.copy(pedCalculatedDate = LocalDate.of(2000, 1, 1), pedOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.APD, minimal.copy(apdCalculatedDate = LocalDate.of(2000, 1, 1), apdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.NPD, minimal.copy(npdCalculatedDate = LocalDate.of(2000, 1, 1), npdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.LED, minimal.copy(ledCalculatedDate = LocalDate.of(2000, 1, 1), ledOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.SED, minimal.copy(sedCalculatedDate = LocalDate.of(2000, 1, 1), sedOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.PRRD, minimal.copy(prrdCalculatedDate = LocalDate.of(2000, 1, 1), prrdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.Tariff, minimal.copy(tariffCalculatedDate = LocalDate.of(2000, 1, 1), tariffOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.DPRRD, minimal.copy(dprrdCalculatedDate = LocalDate.of(2000, 1, 1), dprrdOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        Arguments.of(ReleaseDateType.TUSED, minimal.copy(tusedCalculatedDate = LocalDate.of(2000, 1, 1), tusedOverriddenDate = null), LocalDate.of(2000, 1, 1)),
        // types that only have override
        Arguments.of(ReleaseDateType.ERSED, minimal.copy(ersedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.TERSED, minimal.copy(tersedOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
        Arguments.of(ReleaseDateType.ROTL, minimal.copy(rotlOverriddenDate = LocalDate.of(2010, 10, 10)), LocalDate.of(2010, 10, 10)),
      )
    }
  }
}
