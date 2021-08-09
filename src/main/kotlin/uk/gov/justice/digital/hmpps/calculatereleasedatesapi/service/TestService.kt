package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TestRepository

@Service
class TestService(
  private val testRepository: TestRepository,
  private val prisonService: PrisonService,
) {

  fun getTestData(): List<TestData> {
    val prisoner = prisonService.getOffenderDetail("A1234AA")
    val testData = testRepository.findAll().map { transform(it) }.toMutableList()
    testData.add(TestData(prisoner.offenderNo, prisoner.bookingId))
    return testData
  }
}
