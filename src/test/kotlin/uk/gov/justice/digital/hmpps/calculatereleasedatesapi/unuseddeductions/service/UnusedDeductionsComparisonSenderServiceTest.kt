package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ComparisonStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.prisonapi.model.CalculablePrisoner
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.BulkComparisonEventPublisher
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ServiceUserService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository
import java.util.Optional

class UnusedDeductionsComparisonSenderServiceTest {
  private val prisonService: PrisonService = mock()
  private val publisher: BulkComparisonEventPublisher = mock()
  private val serviceUserService: ServiceUserService = mock()
  private val comparisonRepository: UnusedDeductionsComparisonRepository = mock()
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository = mock()
  private val completer = UnusedDeductionsComparisonSetupCompleter(comparisonRepository, comparisonPersonRepository)
  private val sender = UnusedDeductionsComparisonSenderService(prisonService, publisher, serviceUserService, completer)

  private val comparison = UnusedDeductionsComparison(
    id = 1,
    criteria = TestUtil.objectMapper().createObjectNode(),
    comparisonType = UnusedDeductionsComparisonType.MANUAL,
    calculatedByUsername = "FOO",
  )

  private fun prisoner(number: String) = CalculablePrisoner(prisonerNumber = number, bookingId = 1)

  private fun givenComparison() {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.of(comparison))
    whenever(serviceUserService.getUsername()).thenReturn("FOO")
  }

  @Test
  fun `manual run sends a message per prisoner and moves to processing`() {
    givenComparison()

    sender.processManual(1, listOf("A1", "B2"), "token")

    verify(publisher).sendUnusedDeductionsMessageBatch(1, listOf("A1", "B2"), "FOO", null)
    assertThat(comparison.numberOfPeopleExpected).isEqualTo(2)
    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.PROCESSING)
  }

  @Test
  fun `single prison run uses the prisoners of that prison`() {
    givenComparison()
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1")))

    sender.processPrison(1, "MDI", "token")

    verify(publisher).sendUnusedDeductionsMessageBatch(1, listOf("A1"), "FOO", null)
    assertThat(comparison.numberOfPeopleExpected).isEqualTo(1)
  }

  @Test
  fun `all prisons run tags each message with its establishment and totals the prisoners`() {
    givenComparison()
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf("MDI", "LEI").map { Agency(it, it) })
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenReturn(listOf(prisoner("A1"), prisoner("B2")))
    whenever(prisonService.getCalculablePrisonerByPrison("LEI")).thenReturn(listOf(prisoner("C3")))

    sender.processAllPrisons(1, "token")

    verify(publisher).sendUnusedDeductionsMessageBatch(1, listOf("A1", "B2"), "FOO", "MDI")
    verify(publisher).sendUnusedDeductionsMessageBatch(1, listOf("C3"), "FOO", "LEI")
    assertThat(comparison.numberOfPeopleExpected).isEqualTo(3)
  }

  @Test
  fun `all prisons run skips a prison unknown to prison api and carries on`() {
    givenComparison()
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf("AKI", "LEI").map { Agency(it, it) })
    whenever(prisonService.getCalculablePrisonerByPrison("AKI")).thenThrow(
      WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null),
    )
    whenever(prisonService.getCalculablePrisonerByPrison("LEI")).thenReturn(listOf(prisoner("C3")))

    sender.processAllPrisons(1, "token")

    verify(publisher).sendUnusedDeductionsMessageBatch(1, listOf("C3"), "FOO", "LEI")
    assertThat(comparison.numberOfPeopleExpected).isEqualTo(1)
    assertThat(comparison.comparisonStatus).isNotEqualTo(ComparisonStatus.ERROR)
  }

  @Test
  fun `all prisons run still fails on other prison api errors`() {
    givenComparison()
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(listOf("MDI").map { Agency(it, it) })
    whenever(prisonService.getCalculablePrisonerByPrison("MDI")).thenThrow(
      WebClientResponseException.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Boom", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null),
    )

    sender.processAllPrisons(1, "token")

    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.ERROR)
  }

  @Test
  fun `setup completes immediately when there is nobody to compare`() {
    givenComparison()

    sender.processManual(1, emptyList(), "token")

    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.COMPLETED)
  }

  @Test
  fun `setup completes if every message was already processed before setup finished`() {
    givenComparison()
    whenever(comparisonPersonRepository.countByComparisonId(1)).thenReturn(2)

    sender.processManual(1, listOf("A1", "B2"), "token")

    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.COMPLETED)
    assertThat(comparison.numberOfPeopleCompared).isEqualTo(2)
  }

  @Test
  fun `an error while sending marks the comparison as errored`() {
    givenComparison()
    whenever(publisher.sendUnusedDeductionsMessageBatch(any(), any(), any(), anyOrNull())).thenThrow(RuntimeException("sqs down"))

    sender.processManual(1, listOf("A1"), "token")

    assertThat(comparison.comparisonStatus).isEqualTo(ComparisonStatus.ERROR)
  }

  @Test
  fun `a missing comparison during an error is ignored`() {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.empty())

    completer.handleErrorInSetup(1, RuntimeException("x"))

    verify(comparisonRepository, never()).save(any<UnusedDeductionsComparison>())
  }

  @Test
  fun `completing setup for an unknown comparison fails`() {
    whenever(comparisonRepository.findById(1)).thenReturn(Optional.empty())

    org.junit.jupiter.api.assertThrows<EntityNotFoundException> { completer.completeSetup(1, 1) }
  }
}
