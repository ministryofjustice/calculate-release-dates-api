package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.springframework.core.io.ClassPathResource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.time.LocalDate

/**
 * This test just takes a booking JSON file and anonymises any data and removes duplicate sentences.
 * Work in progress. Copy data from build file after.
 */
class BookingAnonymiser {
  private val jsonTransformation = JsonTransformation()

  @Test
  fun anonymiseTestCase() {
    val exampleType = "custom-examples"
    val exampleNumber = "crs-2162-2"
    var (booking, calculationUserInputs) = jsonTransformation.loadBooking("$exampleType/$exampleNumber")

    var sentences = booking.sentences.map {
      if (it is StandardDeterminateSentence) {
        return@map it.copy(
          lineSequence = null,
          caseReference = null,
          caseSequence = null,
        )
      } else if (it is ExtendedDeterminateSentence) {
        return@map it.copy(
          lineSequence = null,
          caseReference = null,
          caseSequence = null,
        )
      } else {
        return@map it
      }
    }

    sentences = sentences.distinctBy {
      if (booking.sentences.any { consec ->
          consec.consecutiveSentenceUUIDs.contains(it.identifier)
        }
      ) {
        return@distinctBy "${it.identifier}"
      } else if (it is StandardDeterminateSentence) {
        return@distinctBy "${it.duration}${it.sentencedAt}${it.recallType}${it.isSDSPlus}${it.hasAnSDSEarlyReleaseExclusion}${it.consecutiveSentenceUUIDs.firstOrNull()}"
      } else if (it is ExtendedDeterminateSentence) {
        return@distinctBy "${it.custodialDuration}${it.extensionDuration}${it.sentencedAt}${it.recallType}${it.consecutiveSentenceUUIDs.firstOrNull()}"
      } else {
        return@distinctBy "${it.identifier}"
      }
    }

    booking = booking.copy(
      bookingId = 0,
      offender = booking.offender.copy(reference = "ABC123", dateOfBirth = LocalDate.of(1990, 1, 1)),
      sentences = sentences,
    )

    val mapper = TestUtil.minimalTestCaseMapper()

    val jsonTree: ObjectNode = mapper.valueToTree(booking)
    jsonTree.put("calculateErsed", calculationUserInputs.calculateErsed)

    TestUtil.minimalTestCaseMapper().writeValue(ClassPathResource("test_data/overall_calculation/$exampleType/$exampleNumber.json").file, jsonTree)

    // Then copy file manually from build dir.
  }
}
