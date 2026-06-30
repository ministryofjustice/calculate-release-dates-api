package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client.ManageUsersApiClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequestSecondCheck
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.manageusersapi.model.PrisonUserBasicDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationViewConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SecondCheckDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculationSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.SecondCheckRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class HistoricCalculationsServiceTest {
  @Mock
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Mock
  lateinit var secondCheckRepository: SecondCheckRepository

  @Mock
  lateinit var prisonService: PrisonService

  @Mock
  lateinit var manageUsersApiClient: ManageUsersApiClient

  @InjectMocks
  lateinit var underTest: HistoricCalculationsService
  val reference: UUID = UUID.randomUUID()

  private val usersDetails = mapOf(
    "USER" to PrisonUserBasicDetails(
      username = "CRD_TEST_USER",
      firstName = "Crd",
      lastName = "Test User",
      authSource = PrisonUserBasicDetails.AuthSource.nomis,
      enabled = true,
      staffId = 12345,
      userId = 67890,
      name = "Crd Test User",
    ),
    "USER1" to PrisonUserBasicDetails(
      username = "CRD_TEST_USER",
      firstName = "Crd",
      lastName = "Test User 1",
      authSource = PrisonUserBasicDetails.AuthSource.nomis,
      enabled = true,
      staffId = 12345,
      userId = 67890,
      name = "Crd Test User",
    ),
  )

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
      reasonForCalculation = CalculationReason(id = 1, isActive = true, isOther = false, displayName = "calc reason", isBulk = false, nomisReason = null, nomisComment = null, displayRank = 1, useForApprovedDates = false, eligibleForPreviouslyRecordedSled = false, requiresFurtherDetail = false, furtherDetailDescription = null, isSecondCheck = false),
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
  fun `Test second check for a CRDS request`() {
    val calcRequest1 = calculationRequest()
    val secondCheck1 = secondCheckRecord().copy(
      checkedAt = LocalDateTime.now().plusSeconds(1),
      checkedByUsername = "USER",
    )
    val secondCheck2 = secondCheckRecord().copy(
      checkedAt = LocalDateTime.now().plusSeconds(2),
      checkedByUsername = "USER1",
    )
    val secondCheck3 = secondCheckRecord().copy(
      checkedAt = LocalDateTime.now().plusSeconds(3),
      checkedByUsername = "UNKNOWN",
    )
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calcRequest1))
    whenever(secondCheckRepository.findAllByPrisonerId(anyString())).thenReturn(listOf(secondCheck1, secondCheck2, secondCheck3))
    whenever(manageUsersApiClient.getUsersByUsernames(anySet())).thenReturn(usersDetails)

    val sentenceCalculationSummary1 = sentenceCalculationSummary("comment $reference")
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary1))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[0].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
    assertThat(result[0].establishment).isEqualTo("Chelmsford (HMP)")
    assertThat(result[0].calculationReason).isNull()
    assertThat(result[0].secondCheckDetails).hasSize(3)
    assertThat(result[0].secondCheckDetails[2]).isEqualTo(
      SecondCheckDetails("USER", "Crd Test User", secondCheck1.checkedAt),
    )
    assertThat(result[0].secondCheckDetails[1]).isEqualTo(
      SecondCheckDetails("USER1", "Crd Test User 1", secondCheck2.checkedAt),
    )
    assertThat(result[0].secondCheckDetails[0]).isEqualTo(
      SecondCheckDetails("UNKNOWN", "", secondCheck3.checkedAt),
    )
  }

  @Test
  fun `Test no second check in CRDS with only NOMIS history`() {
    val calcRequest1 = calculationRequest()
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calcRequest1))
    whenever(secondCheckRepository.findAllByPrisonerId(anyString())).thenReturn(emptyList())

    val sentenceCalculationSummary1 = sentenceCalculationSummary("test comment")
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary1))
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.NOMIS)
    assertThat(result[0].calculationReason).isEqualTo("reason")
  }

  @Test
  fun `Test source set to NOMIS if calculation not found in database`() {
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calculationRequest()))
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary("comment")))
    whenever(manageUsersApiClient.getUsersByUsernames(anySet())).thenReturn(usersDetails)
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(1)
    assertThat(result[0].calculatedByDisplayName).isEqualTo("Crd Test User")
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
      reasonForCalculation = CalculationReason(id = 1, isActive = true, isOther = false, displayName = "calc reason", isBulk = false, nomisReason = null, nomisComment = null, displayRank = 1, useForApprovedDates = false, eligibleForPreviouslyRecordedSled = false, requiresFurtherDetail = false, furtherDetailDescription = null, isSecondCheck = false),
      genuineOverrideReason = GenuineOverrideReason.OTHER,
      genuineOverrideReasonFurtherDetail = "Some more details",
    )
    whenever(calculationRequestRepository.findAllByPrisonerIdAndCalculationStatus(anyString(), anyString())).thenReturn(listOf(calcRequest1, calcRequest2))

    val sentenceCalculationSummary1 = sentenceCalculationSummary("comment $reference")
    val sentenceCalculationSummary2 = sentenceCalculationSummary1.copy(bookingId = 3, commentText = "comment: ${calcRequest2.calculationReference}", calculatedByUserId = "user1")
    whenever(prisonService.getCalculationsForAPrisonerId(anyString())).thenReturn(listOf(sentenceCalculationSummary1, sentenceCalculationSummary2))
    whenever(manageUsersApiClient.getUsersByUsernames(anySet())).thenReturn(usersDetails)
    val result = underTest.getHistoricCalculationsForPrisoner("123")
    assertThat(result).hasSize(2)
    assertThat(result[0].calculatedByDisplayName).isEqualTo("Crd Test User")
    assertThat(result[0].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[0].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(reference.toString(), 1))
    assertThat(result[0].establishment).isEqualTo("Chelmsford (HMP)")
    assertThat(result[0].genuineOverrideReasonCode).isEqualTo(GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE)
    assertThat(result[0].genuineOverrideReasonDescription).isEqualTo("One or more offences have been characterised by an aggravating factor (such as terror)")

    assertThat(result[1].calculatedByDisplayName).isEqualTo("Crd Test User 1")
    assertThat(result[1].calculationSource).isEqualTo(CalculationSource.CRDS)
    assertThat(result[1].calculationViewConfiguration).isEqualTo(CalculationViewConfiguration(calcRequest2.calculationReference.toString(), 1))
    assertThat(result[1].establishment).isEqualTo("HMP KENNET")
    assertThat(result[1].calculationReason).isEqualTo(calcRequest2.reasonForCalculation?.displayName)
    assertThat(result[1].genuineOverrideReasonCode).isEqualTo(GenuineOverrideReason.OTHER)
    assertThat(result[1].genuineOverrideReasonDescription).isEqualTo("Some more details")
  }

  private fun sentenceCalculationSummary(comment: String): SentenceCalculationSummary = SentenceCalculationSummary(456, "123", "bob", "davies", "RNI", "Ranby (HMP)", 1, LocalDateTime.now(), 4, comment, "reason", "user", "User", "One")

  private fun calculationRequest(): CalculationRequest = CalculationRequest(1, reference, "123", 4565, CalculationStatus.CONFIRMED.name, calculatedAt = LocalDateTime.now(), prisonerLocation = "CDI")
  private fun secondCheckRecord(): CalculationRequestSecondCheck = CalculationRequestSecondCheck(1, calculationRequest().id(), "123", checkedByUsername = "CRD_TEST_USER")

  val calculationReason = CalculationReason(
    id = 18,
    isActive = true,
    isOther = false,
    isBulk = false,
    displayName = "Reason",
    nomisReason = "UPDATE",
    nomisComment = "NOMIS_COMMENT",
    displayRank = null,
    useForApprovedDates = false,
    eligibleForPreviouslyRecordedSled = false,
    requiresFurtherDetail = false,
    furtherDetailDescription = null,
    isSecondCheck = false,
  )
}
