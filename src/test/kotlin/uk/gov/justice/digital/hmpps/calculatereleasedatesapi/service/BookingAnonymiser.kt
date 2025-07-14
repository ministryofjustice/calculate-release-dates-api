package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AFineSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BotusSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SopcSentence
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
  fun anonymiseSingleTestCases() {
    val exampleType = "custom-examples"
    val exampleNumber = "crs-2331-bug"

    anonymiseTestCase("$exampleType/$exampleNumber")
  }

  //  @Test
  fun anonymiseAllTestCases() {
    val allFiles = jsonTransformation.getAllJsonFromDir("overall_calculation/")
    allFiles.forEach { (exampleNumber, _) ->
      anonymiseTestCase(exampleNumber)
    }
  }

  private fun anonymiseTestCase(example: String) {
    val calculationFile = jsonTransformation.loadCalculationTestFile(example)
    var sentences = calculationFile.booking.sentences.map {
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
      } else if (it is BotusSentence) {
        return@map it.copy(
          lineSequence = null,
          caseSequence = null,
        )
      } else if (it is SopcSentence) {
        return@map it.copy(
          lineSequence = null,
          caseReference = null,
          caseSequence = null,
        )
      } else if (it is DetentionAndTrainingOrderSentence) {
        return@map it.copy(
          lineSequence = null,
          caseReference = null,
          caseSequence = null,
        )
      } else if (it is AFineSentence) {
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
      if (calculationFile.booking.sentences.any { consec ->
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

    val booking = calculationFile.booking.copy(
      bookingId = 0,
      offender = calculationFile.booking.offender.copy(reference = "ABC123", dateOfBirth = LocalDate.of(1990, 1, 1)),
      sentences = sentences,
    )

    val mapper = TestUtil.minimalTestCaseMapper()

    val jsonTree: ObjectNode = mapper.valueToTree(calculationFile.copy(booking))

    val bookingTree = jsonTree.get("booking")
    val sentencesArray = bookingTree.get("sentences")
    val sentencesString = sentencesArray.toString()
    sentencesArray.forEach {
      val obj = it as ObjectNode
      val type = it.get("type").asText()
      if (type == "StandardSentence") {
        obj.remove("type")
      }

      if (obj.has("hasAnSDSEarlyReleaseExclusion") && obj.get("hasAnSDSEarlyReleaseExclusion").asText() == "NO") {
        obj.remove("hasAnSDSEarlyReleaseExclusion")
      }

      val identifier = obj.get("identifier").asText()
      if (sentencesString.split(identifier).size == 2) { // Identifier only appears once. Not needed for a consecutive sentence.
        obj.remove("identifier")
      }
    }

    val adjustmentsObject = bookingTree.get("adjustments")
    if (adjustmentsObject.isEmpty) {
      jsonTree.remove("adjustments")
    }

    log.info("Anonomised data: ${TestUtil.minimalTestCaseMapper().writeValueAsString(jsonTree)}")
    TestUtil.minimalTestCaseMapper().writeValue(ClassPathResource("test_data/overall_calculation/$example.json").file, jsonTree)
    // Then copy file manually from build dir.
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
