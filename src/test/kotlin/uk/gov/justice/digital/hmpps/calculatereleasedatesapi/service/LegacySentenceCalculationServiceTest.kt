package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aFullyCompletedLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.LegacySentenceCalculationTest.Companion.aMinimalLegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PrisonerModifiedReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.migration.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.LegacySentenceCalculationRepository
import java.util.Optional
import java.util.UUID
import kotlin.test.junit5.JUnit5Asserter.fail

class LegacySentenceCalculationServiceTest {

  private val calculationRequestRepository: CalculationRequestRepository = mock()
  private val legacySentenceCalculationRepository: LegacySentenceCalculationRepository = mock()
  private val approvedDatesSubmissionRepository: ApprovedDatesSubmissionRepository = mock()

  private val service = LegacySentenceCalculationService(calculationRequestRepository, legacySentenceCalculationRepository, approvedDatesSubmissionRepository)

  @Test
  fun `migration maps all fields from request to entity when there is no mapping to a CRDS calculation`() {
    val aMinimalRequest = aMinimalLegacySentenceCalculationDto()
    val aFullyCompletedRequest = aFullyCompletedLegacySentenceCalculationDto()

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequest, null)
    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequest, null)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      val entity = invocation.getArgument<LegacySentenceCalculation>(0)
      when (entity.nomisCalculationId) {
        aMinimalRequest.nomisCalculationId -> expectedMinimalEntity
        expectedFullyCompleteEntity.nomisCalculationId -> expectedFullyCompleteEntity
        else -> fail("Unexpected id")
      }
    }

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequest, aFullyCompletedRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(1, null),
          LegacySentenceCalculationToCrdsMappingResult(2, null),
        ),
      ),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
  }

  @Test
  fun `migration finds a UUID within a request and links it to the calculation`() {
    val calculationReference = UUID.randomUUID()
    val calculationRequestId = 123456L

    val aMinimalRequestWithNoMatch = aMinimalLegacySentenceCalculationDto()
    val aFullyCompletedRequestWithAMatchingCalculationRequest = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReference")

    val calculationRequest = CalculationRequest(id = calculationRequestId, calculationReference = calculationReference, prisonerId = aFullyCompletedRequestWithAMatchingCalculationRequest.prisonerId)

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequestWithNoMatch, null)
    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequestWithAMatchingCalculationRequest, calculationRequest)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      val entity = invocation.getArgument<LegacySentenceCalculation>(0)
      when (entity.nomisCalculationId) {
        aMinimalRequestWithNoMatch.nomisCalculationId -> expectedMinimalEntity
        expectedFullyCompleteEntity.nomisCalculationId -> expectedFullyCompleteEntity
        else -> fail("Unexpected id")
      }
    }

    whenever(calculationRequestRepository.findByCalculationReference(calculationReference)).thenReturn(Optional.of(calculationRequest))

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequestWithNoMatch, aFullyCompletedRequestWithAMatchingCalculationRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(1, null),
          LegacySentenceCalculationToCrdsMappingResult(2, calculationRequestId),
        ),
      ),
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

    val aMinimalRequestWithNoMatch = aMinimalLegacySentenceCalculationDto()
    val aFullyCompletedRequestWithAMatchingCalculationRequest = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReference")

    val calculationRequestForADifferentPrisonerId = CalculationRequest(id = calculationRequestId, calculationReference = calculationReference, prisonerId = "Someone Else")

    val expectedMinimalEntity = LegacySentenceCalculation.fromDto(aMinimalRequestWithNoMatch, null)
    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aFullyCompletedRequestWithAMatchingCalculationRequest, calculationRequestForADifferentPrisonerId)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenAnswer { invocation ->
      val entity = invocation.getArgument<LegacySentenceCalculation>(0)
      when (entity.nomisCalculationId) {
        aMinimalRequestWithNoMatch.nomisCalculationId -> expectedMinimalEntity
        expectedFullyCompleteEntity.nomisCalculationId -> expectedFullyCompleteEntity
        else -> fail("Unexpected id")
      }
    }

    whenever(calculationRequestRepository.findByCalculationReference(calculationReference)).thenReturn(Optional.of(calculationRequestForADifferentPrisonerId))

    val request = LegacySentenceCalculationMigrationRequest(listOf(aMinimalRequestWithNoMatch, aFullyCompletedRequestWithAMatchingCalculationRequest))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(1, null),
          LegacySentenceCalculationToCrdsMappingResult(2, calculationRequestId),
        ),
      ),
    )

    verify(legacySentenceCalculationRepository).saveAndFlush(expectedMinimalEntity)
    verify(legacySentenceCalculationRepository).saveAndFlush(expectedFullyCompleteEntity)
    verify(calculationRequestRepository).setPrisonerIdForCalculationRequest(eq(calculationRequestId), eq("Someone Else"), eq("AB1234C"), eq(PrisonerModifiedReason.MIGRATION), any())
    verify(approvedDatesSubmissionRepository).setPrisonerIdForCalculationRequest(eq(calculationRequestId), eq("Someone Else"), eq("AB1234C"), eq(PrisonerModifiedReason.MIGRATION), any())
  }

  @Test
  fun `migration handles being unable to find the calculation request gracefully`() {
    val calculationReference = UUID.randomUUID()

    val aCalcWithAUUID = aFullyCompletedLegacySentenceCalculationDto().copy(commentText = "A CRDS calc: $calculationReference")

    val expectedFullyCompleteEntity = LegacySentenceCalculation.fromDto(aCalcWithAUUID, null)

    whenever(legacySentenceCalculationRepository.saveAndFlush(any<LegacySentenceCalculation>())).thenReturn(expectedFullyCompleteEntity)
    whenever(calculationRequestRepository.findByCalculationReference(calculationReference)).thenReturn(Optional.empty())

    val request = LegacySentenceCalculationMigrationRequest(listOf(aCalcWithAUUID))

    val response = service.migrate("AB1234C", request)

    assertThat(response).isEqualTo(
      LegacySentenceCalculationMigrationResponse(
        listOf(
          LegacySentenceCalculationToCrdsMappingResult(2, null),
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
}
