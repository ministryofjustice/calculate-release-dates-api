package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.node.ObjectNode
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

//  @Test
  fun anonymiseTestCase() {
    val exampleType = "validation"
    val exampleNumber = "crs-2194-immediate-release-future-dated"
    var (booking, calculationUserInputs) = jsonTransformation.loadBooking("$exampleType/$exampleNumber")

    booking = booking.copy(
      bookingId = 0,
      offender = booking.offender.copy(reference = "ABC123", dateOfBirth = LocalDate.of(1990, 1, 1)),
      sentences = booking.sentences.map {
        if (it is StandardDeterminateSentence) {
          it.copy(
            offence = it.offence.copy(offenceCode = null),
            lineSequence = null,
            caseReference = null,
            caseSequence = null,
          )
        } else if (it is ExtendedDeterminateSentence) {
          it.copy(
            offence = it.offence.copy(offenceCode = null),
            lineSequence = null,
            caseReference = null,
            caseSequence = null,
          )
        } else {
          it
        }
      }.distinctBy {
        if (booking.sentences.any { consec ->
            consec.consecutiveSentenceUUIDs.contains(it.identifier)
          }
        ) {
          it.identifier
        } else if (it is StandardDeterminateSentence) {
          "${it.duration}${it.sentencedAt}${it.recallType}${it.isSDSPlus}${it.hasAnSDSEarlyReleaseExclusion}"
        } else if (it is ExtendedDeterminateSentence) {
          "${it.custodialDuration}${it.extensionDuration}${it.sentencedAt}${it.recallType}"
        } else {
          it.identifier
        }
      },
    )

    val mapper = TestUtil.minimalTestCaseMapper()

    val jsonTree: ObjectNode = mapper.valueToTree(booking)
    jsonTree.put("calculateErsed", calculationUserInputs.calculateErsed)

    TestUtil.minimalTestCaseMapper().writeValue(ClassPathResource("test_data/overall_calculation/$exampleType/$exampleNumber.json").file, jsonTree)

    // Then copy file manually from build dir.
  }
}
