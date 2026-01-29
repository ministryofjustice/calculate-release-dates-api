package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class HistoricCalculationsServiceTest {
  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var prisonService: PrisonService

  @InjectMocks
  lateinit var underTest: HistoricCalculationsService
  val reference: UUID = UUID.randomUUID()

  @BeforeEach
  fun beforeEach() {
    val agencies = listOf(Agency("KTI", "HMP KENNET"), Agency("CDI", "Chelmsford (HMP)"))
    whenever(prisonService.getAgenciesByType("INST")).thenReturn(agencies)
  }

  @Test
  fun `Test source set to CRDS if calculation found in database`() {
    val calcRequest1 = calculationRequest()
    val calcRequest2 = calcRequest1.copy(
      prisonerLocation = "KTI",
      calculationReference = UUID.randomUUID(),
      reasonForCalculation = CalculationReason(id = 1, isActive = true, isOther = false, displayName = "calc reason", isBulk = false, nomisReason = null, nomisComment = null, displayRank = 1, useForApprovedDates = false, eligibleForPreviouslyRecordedSled = false, requiresFurtherDetail = false, furtherDetailDescription = null),
    )
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calcRequest1, calcRequest2))

    val sentenceCalculationSummary1 = sentenceCalculationSummary("comment $reference")
    val sentenceCalculationSummary2 = sentenceCalculationSummary1.copy(bookingId = 3, commentText = "comment: ${calcRequest2.calculationReference}")
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary1, sentenceCalculationSummary2))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(2)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[0].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
    assertThat(result[0].establishment).isEqualTo("Chelmsford (HMP)")
    assertThat(result[0].calculationReason).isNull()

    assertThat(result[1].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[1].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(calcRequest2.calculationReference.toString(), 1))
    assertThat(result[1].establishment).isEqualTo("HMP KENNET")
    assertThat(result[1].calculationReason).isEqualTo(calcRequest2.reasonForCalculation?.displayName)
  }

  @Test
  fun `Test source set to NOMIS if calculation not found in database`() {
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calculationRequest()))
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary("comment")))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.NOMIS)
    assertThat(result[0].calculationViewConfiguration).isNull()
    assertThat(result[0].establishment).isNull()
  }

  @Test
  fun `Genuine override details added if CRDS calculation found in database`() {
    val calcRequest1 = calculationRequest().copy(genuineOverrideReason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE, genuineOverrideReasonFurtherDetail = null)
    val calcRequest2 = calcRequest1.copy(
      prisonerLocation = "KTI",
      calculationReference = UUID.randomUUID(),
      reasonForCalculation = CalculationReason(id = 1, isActive = true, isOther = false, displayName = "calc reason", isBulk = false, nomisReason = null, nomisComment = null, displayRank = 1, useForApprovedDates = false, eligibleForPreviouslyRecordedSled = false, requiresFurtherDetail = false, furtherDetailDescription = null),
      genuineOverrideReason = GenuineOverrideReason.OTHER,
      genuineOverrideReasonFurtherDetail = "Some more details",
    )
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calcRequest1, calcRequest2))

    val sentenceCalculationSummary1 = sentenceCalculationSummary("comment $reference")
    val sentenceCalculationSummary2 = sentenceCalculationSummary1.copy(bookingId = 3, commentText = "comment: ${calcRequest2.calculationReference}")
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary1, sentenceCalculationSummary2))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(2)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[0].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
    assertThat(result[0].establishment).isEqualTo("Chelmsford (HMP)")
    assertThat(result[0].genuineOverrideReasonCode).isEqualTo(GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE)
    assertThat(result[0].genuineOverrideReasonDescription).isEqualTo("One or more offences have been characterised by an aggravating factor (such as terror)")

    assertThat(result[1].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[1].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(calcRequest2.calculationReference.toString(), 1))
    assertThat(result[1].establishment).isEqualTo("HMP KENNET")
    assertThat(result[1].calculationReason).isEqualTo(calcRequest2.reasonForCalculation?.displayName)
    assertThat(result[1].genuineOverrideReasonCode).isEqualTo(GenuineOverrideReason.OTHER)
    assertThat(result[1].genuineOverrideReasonDescription).isEqualTo("Some more details")
  }

  private fun sentenceCalculationSummary(comment: String): SentenceCalculationSummary = SentenceCalculationSummary(456, "123", "bob", "davies", "RNI", "Ranby (HMP)", 1, LocalDateTime.now(), 4, comment, "reason", "user", "User", "One")

  private fun calculationRequest(): CalculationRequest = CalculationRequest(1, reference, "123", 4565, CalculationStatus.CONFIRMED.name, calculatedAt = LocalDateTime.now(), prisonerLocation = "CDI")
}
