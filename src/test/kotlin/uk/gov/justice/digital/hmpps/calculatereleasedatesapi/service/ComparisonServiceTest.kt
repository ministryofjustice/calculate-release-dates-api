package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.AuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonRepository
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ComparisonServiceTest : IntegrationTestBase() {

  private val prisonService = mock<PrisonService>()

  @Autowired
  lateinit var comparisonService: ComparisonService

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `A Comparison is created when create is called`() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList())),
    )

    val comparisonInput = ComparisonInput(criteria = objectMapper.createObjectNode(), manualInput = false, prison = null)
    val comparison = comparisonService.create(comparisonInput)
    Assertions.assertEquals(comparison.manualInput, false)
    Assertions.assertEquals(comparison.prison, null)
  }

  @Test
  fun `A Comparison is created when create is called with manual input`() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList())),
    )

    val comparisonInput = ComparisonInput(criteria = objectMapper.createObjectNode(), manualInput = true, prison = "ABC")
    val comparison = comparisonService.create(comparisonInput)
    Assertions.assertEquals(comparison.manualInput, true)
    Assertions.assertEquals(comparison.prison, "ABC")
  }

  @Test
  fun `Get a list of comparisons`() {
    val prisonService = mock<PrisonService>()
    val comparisonRepository = mock<ComparisonRepository>()
    val comparisonPersonRepository = mock<ComparisonPersonRepository>()
    val serviceUserService = mock<ServiceUserService>()

    // Creating an alternative comparison service so that we can mock the prisonService response
    val alternativeComparisonService = ComparisonService(comparisonRepository, prisonService, serviceUserService, comparisonPersonRepository)

    Mockito.`when`(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("ABC"))
    Mockito.`when`(comparisonRepository.findAllByManualInputAndPrisonIsIn(any(), any())).thenReturn(
      listOf(
        Comparison(
          1,
          UUID.randomUUID(),
          "ABCD1234",
          objectMapper.createObjectNode(),
          "ABC",
          false,
          LocalDateTime.now(),
          "JOEL",
        ),
      ),
    )

    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList())),
    )

    val comparisonList = alternativeComparisonService.listComparisons()

    Assertions.assertEquals(comparisonList.size, 1)
  }

  @Test
  fun `Get a list of manual comparisons`() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList())),
    )

    val comparisonInput = ComparisonInput(criteria = objectMapper.createObjectNode(), manualInput = true, prison = null)
    comparisonService.create(comparisonInput)

    val manualComparisonList = comparisonService.listManual()
    Assertions.assertTrue(manualComparisonList.isNotEmpty())
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with unknown reference`() {
    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference("ABCD1234")
    Assertions.assertEquals(numberOfPeople, 0)
  }

  @Test
  fun `Get a count of people associated with a single calculation reference with valid reference`() {
    SecurityContextHolder.setContext(
      SecurityContextImpl(AuthAwareAuthenticationToken(FAKE_TOKEN, USERNAME, emptyList())),
    )

    val comparisonInput = ComparisonInput(criteria = objectMapper.createObjectNode(), manualInput = false, prison = null)
    val comparison = comparisonService.create(comparisonInput)
    Assertions.assertEquals(comparison.manualInput, false)
    Assertions.assertEquals(comparison.prison, null)

    val numberOfPeople = comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparison.comparisonShortReference)
    Assertions.assertEquals(numberOfPeople, 0)
  }

  companion object {
    const val USERNAME = "user1"
    val FAKE_TOKEN: Jwt = Jwt
      .withTokenValue("123")
      .header("header1", "value1")
      .claim("claim1", "value1")
      .build()
  }
}
