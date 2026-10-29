package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.CalculationTestFile
import java.time.LocalDate

/**
 * This test just takes a booking JSON file and anonymises any data and removes duplicate sentences.
 * Work in progress. Copy data from build file after.
 */
class BookingAnonymiser {
  private val jsonTransformation = JsonTransformation()

//  @Test
  fun anonymiseSingleTestCases() {
    val exampleType = "progression-model"
    val exampleNumber = "crs-2869-bug-2"

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
    val calculationFile = jsonTransformation.loadCalculationTestFile("overall_calculation/$example")
    val jsonTree = anonymise(calculationFile)
    log.info("Anonomised data: ${TestUtil.minimalTestCaseMapper().writeValueAsString(jsonTree)}")
    TestUtil.minimalTestCaseMapper().writeValue(ClassPathResource("test_data/overall_calculation/$example.json").file, jsonTree)
    // Then copy file manually from build dir.
  }

  fun anonymise(calculationFile: CalculationTestFile, removeDuplicateSentences: Boolean = true): ObjectNode {
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

    if (removeDuplicateSentences) {
      sentences = sentences.distinctBy {
        if (calculationFile.booking.sentences.any { consec ->
            consec.consecutiveSentenceUUIDs.contains(it.identifier)
          }
        ) {
          return@distinctBy "${it.identifier}"
        } else if (it is StandardDeterminateSentence) {
          return@distinctBy "${it.duration}${it.sentencedAt}${it.recallType}${it.releaseArrangements}${it.consecutiveSentenceUUIDs.firstOrNull()}"
        } else if (it is ExtendedDeterminateSentence) {
          return@distinctBy "${it.custodialDuration}${it.extensionDuration}${it.sentencedAt}${it.recallType}${it.consecutiveSentenceUUIDs.firstOrNull()}"
        } else {
          return@distinctBy "${it.identifier}"
        }
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
        // default the release arrangements which are removed by NON_DEFAULT which in turn blow up when parsing the sentence
        val releaseArrangements = obj.get("releaseArrangements") as ObjectNode
        if (!releaseArrangements.has("isSDSPlus")) {
          releaseArrangements.put("isSDSPlus", false)
        }
        if (!releaseArrangements.has("isSDSPlusEligibleSentenceTypeLengthAndOffence")) {
          releaseArrangements.put("isSDSPlusEligibleSentenceTypeLengthAndOffence", false)
        }
        if (!releaseArrangements.has("sdsEarlyReleaseExclusions")) {
          releaseArrangements.put("sdsEarlyReleaseExclusions", mapper.createArrayNode())
        }
        if (!releaseArrangements.has("isSection250")) {
          releaseArrangements.put("isSection250", false)
        }
      }

//      val identifier = obj.get("identifier").asText()
//      if (sentencesString.split(identifier).size == 2) { // Identifier only appears once. Not needed for a consecutive sentence.
//        obj.remove("identifier")
//      }

      if (obj.has("externalSentenceId")) {
        obj.remove("externalSentenceId")
      }
    }

    val adjustmentsObject = bookingTree.get("adjustments")
    if (adjustmentsObject.isEmpty) {
      jsonTree.remove("adjustments")
    }

    return jsonTree
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    private const val STAGED_TEST_CASE_DIR = "src/test/resources/test_data/overall_calculation/"

    private val SENTENCE_PII_FIELDS = listOf("caseReference", "caseSequence", "lineSequence", "externalSentenceId")

    // Only the fields the anonymiser scrubs are compared, so serialisation noise in older files isn't flagged.
    private fun piiView(tree: JsonNode): List<Any?> {
      val booking = tree.get("booking")
      val offender = booking?.get("offender")
      return listOf(
        booking?.get("bookingId")?.asLong() ?: 0L,
        offender?.get("reference")?.asText(),
        offender?.get("dateOfBirth")?.asText(),
        booking?.get("sentences")?.map { sentence -> SENTENCE_PII_FIELDS.map { sentence.get(it)?.takeIf { v -> !v.isNull } } },
      )
    }

    /**
     * Pre-commit check. Args are repo relative paths of staged files. Exits 1 if any staged
     * booking differs from its anonymised form. Run via `./gradlew checkBookingAnonymisation`.
     */
    @JvmStatic
    fun main(args: Array<String>) {
      val anonymiser = BookingAnonymiser()
      val mapper = TestUtil.minimalTestCaseMapper()
      val failures = args.filter { it.startsWith(STAGED_TEST_CASE_DIR) && it.endsWith(".json") }.mapNotNull { path ->
        val staged = ProcessBuilder("git", "show", ":$path").redirectErrorStream(false).start()
          .let { it.inputStream.readBytes().toString(Charsets.UTF_8).also { _ -> it.waitFor() } }
        val reason = try {
          val actual = mapper.readTree(staged)
          val anonymised = mapper.readTree(mapper.writeValueAsString(anonymiser.anonymise(mapper.readValue(staged, CalculationTestFile::class.java), removeDuplicateSentences = false)))
          if (piiView(actual) == piiView(anonymised)) null else "differs from the anonymised booking (case references/sequences, booking id, offender reference or date of birth)"
        } catch (e: Exception) {
          "could not be anonymised: ${e.message}"
        }
        reason?.let { "  $path $it" }
      }
      if (failures.isNotEmpty()) {
        System.err.println("Possible PII in staged booking test data:")
        failures.forEach { System.err.println(it) }
        System.exit(1)
      }
    }
  }
}
