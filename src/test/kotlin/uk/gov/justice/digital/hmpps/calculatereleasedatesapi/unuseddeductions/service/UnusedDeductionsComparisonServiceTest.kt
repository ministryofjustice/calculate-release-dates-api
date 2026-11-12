package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CrdWebException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.PrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ServiceUserService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsPageInfo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonPersonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.repository.UnusedDeductionsComparisonRepository

class UnusedDeductionsComparisonServiceTest {
  private val comparisonRepository: UnusedDeductionsComparisonRepository = mock()
  private val comparisonPersonRepository: UnusedDeductionsComparisonPersonRepository = mock()
  private val sender: UnusedDeductionsComparisonSenderService = mock()
  private val prisonService: PrisonService = mock()
  private val serviceUserService: ServiceUserService = mock()
  private val objectMapper = TestUtil.objectMapper()
  private val service = UnusedDeductionsComparisonService(comparisonRepository, comparisonPersonRepository, sender, prisonService, serviceUserService, objectMapper)

  init {
    whenever(serviceUserService.getUsername()).thenReturn("FOO")
    whenever(prisonService.getCurrentUserPrisonsList()).thenReturn(listOf("MDI"))
    whenever(comparisonRepository.save(any<UnusedDeductionsComparison>())).thenAnswer { (it.arguments[0] as UnusedDeductionsComparison).withId() }
    whenever(comparisonPersonRepository.findByComparisonId(any())).thenReturn(emptyList())
  }

  private fun UnusedDeductionsComparison.withId() = UnusedDeductionsComparison(1, comparisonReference, comparisonShortReference, criteria, prison, comparisonType, calculatedAt, calculatedByUsername)

  private fun comparison(type: UnusedDeductionsComparisonType, prison: String?, username: String = "OTHER") = UnusedDeductionsComparison(
    id = 1,
    criteria = objectMapper.createObjectNode(),
    prison = prison,
    comparisonType = type,
    calculatedByUsername = username,
  )

  @Test
  fun `a list of prisoners is a manual run, cleaned and de-duplicated`() {
    val summary = service.create(UnusedDeductionsComparisonInput(prison = "MDI", prisonerIds = listOf(" a1234bc ", "A1234BC", "")), "token")

    assertThat(summary.comparisonType).isEqualTo(UnusedDeductionsComparisonType.MANUAL)
    assertThat(summary.prison).isNull()
    verify(sender).processManual(1, listOf("A1234BC"), "token")
  }

  @Test
  fun `a prison code is a single prison run`() {
    val summary = service.create(UnusedDeductionsComparisonInput(prison = "MDI"), "token")

    assertThat(summary.comparisonType).isEqualTo(UnusedDeductionsComparisonType.PRISON)
    verify(sender).processPrison(1, "MDI", "token")
  }

  @Test
  fun `all is an all prisons run`() {
    val summary = service.create(UnusedDeductionsComparisonInput(prison = "all"), "token")

    assertThat(summary.comparisonType).isEqualTo(UnusedDeductionsComparisonType.ALL_PRISONS)
    verify(sender).processAllPrisons(1, "token")
  }

  @Test
  fun `a single prison outside the callers caseload is forbidden`() {
    val e = assertThrows<CrdWebException> { service.create(UnusedDeductionsComparisonInput(prison = "LEI"), "token") }

    assertThat(e.status.value()).isEqualTo(403)
    verify(comparisonRepository, never()).save(any<UnusedDeductionsComparison>())
  }

  @Test
  fun `nothing to run is a bad request`() {
    val e = assertThrows<CrdWebException> { service.create(UnusedDeductionsComparisonInput(prison = " ", prisonerIds = emptyList()), "token") }

    assertThat(e.status.value()).isEqualTo(400)
  }

  @Test
  fun `criteria records what was requested`() {
    service.create(UnusedDeductionsComparisonInput(prisonerIds = listOf("A1")), "token")

    val captor = argumentCaptor<UnusedDeductionsComparison>()
    verify(comparisonRepository).save(captor.capture())
    assertThat(captor.firstValue.criteria["prisonerIds"][0].asText()).isEqualTo("A1")
  }

  @Test
  fun `list pages the runs viewable by the current user's prisons`() {
    val pageable = PageRequest.of(1, 2)
    whenever(comparisonRepository.findViewable(listOf("MDI"), pageable)).thenReturn(
      PageImpl(listOf(comparison(UnusedDeductionsComparisonType.MANUAL, null)), pageable, 3),
    )

    val result = service.list(page = 1, size = 2)

    assertThat(result.content.map { it.comparisonType }).containsExactly(UnusedDeductionsComparisonType.MANUAL)
    assertThat(result.page).isEqualTo(UnusedDeductionsPageInfo(number = 1, size = 2, totalElements = 3, totalPages = 2))
  }

  @Test
  fun `list can be limited to runs by the current user or by other users`() {
    val pageable = PageRequest.of(0, 10)
    whenever(comparisonRepository.findViewableCalculatedBy(listOf("MDI"), "FOO", pageable)).thenReturn(
      PageImpl(listOf(comparison(UnusedDeductionsComparisonType.MANUAL, null, "FOO")), pageable, 1),
    )
    whenever(comparisonRepository.findViewableNotCalculatedBy(listOf("MDI"), "FOO", pageable)).thenReturn(
      PageImpl(listOf(comparison(UnusedDeductionsComparisonType.ALL_PRISONS, "all", "OTHER")), pageable, 1),
    )

    assertThat(service.list(calculatedByCurrentUser = true).content.map { it.calculatedByUsername }).containsExactly("FOO")
    assertThat(service.list(calculatedByCurrentUser = false).content.map { it.calculatedByUsername }).containsExactly("OTHER")
  }

  @Test
  fun `viewing a single prison run outside the caseload is forbidden`() {
    val c = comparison(UnusedDeductionsComparisonType.PRISON, "LEI")
    whenever(comparisonRepository.findByComparisonShortReference(c.comparisonShortReference)).thenReturn(c)

    val e = assertThrows<CrdWebException> { service.get(c.comparisonShortReference) }

    assertThat(e.status.value()).isEqualTo(403)
  }

  @Test
  fun `an unknown comparison is not found`() {
    assertThrows<EntityNotFoundException> { service.get("nope") }
  }

  @Test
  fun `overview counts outcomes and lists problems before matches`() {
    val c = comparison(UnusedDeductionsComparisonType.MANUAL, null)
    whenever(comparisonRepository.findByComparisonShortReference(c.comparisonShortReference)).thenReturn(c)
    fun person(id: String, outcome: UnusedDeductionsComparisonOutcome) = UnusedDeductionsComparisonPerson(
      comparisonId = 1,
      person = id,
      outcome = outcome,
      existingUnusedDeductions = 0,
      adjustments = objectMapper.createArrayNode(),
      validationMessages = objectMapper.createArrayNode(),
    )
    whenever(comparisonPersonRepository.findByComparisonId(1)).thenReturn(
      listOf(
        person("A", UnusedDeductionsComparisonOutcome.MATCH),
        person("B", UnusedDeductionsComparisonOutcome.MISMATCH),
        person("C", UnusedDeductionsComparisonOutcome.VALIDATION_ERROR),
        person("D", UnusedDeductionsComparisonOutcome.FATAL_ERROR),
      ),
    )

    val overview = service.get(c.comparisonShortReference)

    assertThat(overview.comparison.numberOfMismatches).isEqualTo(1)
    assertThat(overview.comparison.numberOfValidationErrors).isEqualTo(1)
    assertThat(overview.comparison.numberOfPeopleComparisonFailedFor).isEqualTo(1)
    assertThat(overview.people.map { it.person }).containsExactly("B", "C", "D", "A")
  }

  private fun givenPeople(vararg people: Pair<String, UnusedDeductionsComparisonOutcome>): UnusedDeductionsComparison {
    val c = comparison(UnusedDeductionsComparisonType.MANUAL, null)
    whenever(comparisonRepository.findByComparisonShortReference(c.comparisonShortReference)).thenReturn(c)
    whenever(comparisonPersonRepository.findByComparisonId(1)).thenReturn(
      people.map { (id, outcome) ->
        UnusedDeductionsComparisonPerson(
          comparisonId = 1,
          person = id,
          outcome = outcome,
          existingUnusedDeductions = 0,
          adjustments = objectMapper.createArrayNode(),
          validationMessages = objectMapper.createArrayNode(),
        )
      },
    )
    return c
  }

  @Test
  fun `overview can be filtered by outcome`() {
    val c = givenPeople("A" to UnusedDeductionsComparisonOutcome.MATCH, "B" to UnusedDeductionsComparisonOutcome.MISMATCH, "C" to UnusedDeductionsComparisonOutcome.MISMATCH)

    val overview = service.get(c.comparisonShortReference, outcome = UnusedDeductionsComparisonOutcome.MISMATCH)

    assertThat(overview.people.map { it.person }).containsExactly("B", "C")
    assertThat(overview.page.totalElements).isEqualTo(2)
    // the run level counts are not affected by the filter
    assertThat(overview.comparison.numberOfMismatches).isEqualTo(2)
  }

  @Test
  fun `overview can be filtered by part of a prisoner id ignoring case`() {
    val c = givenPeople("A1234BC" to UnusedDeductionsComparisonOutcome.MATCH, "B9999ZZ" to UnusedDeductionsComparisonOutcome.MATCH)

    val overview = service.get(c.comparisonShortReference, prisonerId = " a1234 ")

    assertThat(overview.people.map { it.person }).containsExactly("A1234BC")
  }

  @Test
  fun `overview is paged after sorting and filtering`() {
    val c = givenPeople(
      "A" to UnusedDeductionsComparisonOutcome.MATCH,
      "B" to UnusedDeductionsComparisonOutcome.MISMATCH,
      "C" to UnusedDeductionsComparisonOutcome.MISMATCH,
      "D" to UnusedDeductionsComparisonOutcome.MISMATCH,
      "E" to UnusedDeductionsComparisonOutcome.MATCH,
    )

    val second = service.get(c.comparisonShortReference, page = 1, size = 2)
    val last = service.get(c.comparisonShortReference, page = 2, size = 2)

    assertThat(second.people.map { it.person }).containsExactly("D", "A")
    assertThat(second.page).isEqualTo(UnusedDeductionsPageInfo(number = 1, size = 2, totalElements = 5, totalPages = 3))
    assertThat(last.people.map { it.person }).containsExactly("E")
  }

  @Test
  fun `a page beyond the end is empty`() {
    val c = givenPeople("A" to UnusedDeductionsComparisonOutcome.MATCH)

    val overview = service.get(c.comparisonShortReference, page = 5, size = 10)

    assertThat(overview.people).isEmpty()
    assertThat(overview.page.totalElements).isEqualTo(1)
  }
}
