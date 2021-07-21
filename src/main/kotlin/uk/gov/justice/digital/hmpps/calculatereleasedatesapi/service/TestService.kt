package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.TestData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TestRepository

@Service
class TestService(private val testRepository: TestRepository) {

  fun getTestData(): List<TestData> {
    return testRepository.findAll().map { transform(it) }
  }
}
