package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.EnumSet

@Service
class OffenceSdsPlusLookupService(
  private val manageOffencesService: ManageOffencesService,
) {
  val postPcscCalcTypes: Map<String, EnumSet<SentenceCalculationType>> = mapOf(
    "SDS" to EnumSet.of(
      SentenceCalculationType.ADIMP,
      SentenceCalculationType.ADIMP_ORA,
    ),
    "DYOI" to EnumSet.of(
      SentenceCalculationType.YOI,
      SentenceCalculationType.YOI_ORA,
    ),
    "S250" to EnumSet.of(
      SentenceCalculationType.SEC250,
      SentenceCalculationType.SEC250_ORA,
    ),
  )

  /**
   * Queries MO against Sentences of type and duration that could fit the SDS+ criteria.
   * Sets the [OffenderOffence.PCSC_SDS_PLUS] indicators if it falls under SDS+.
   *
   * @param sentencesAndOffences - A list of [SentenceAndOffence] to process for SDS+
   * @return matchingSentenceMap - A map of bookingId to [SentencesAndOffences] identified as SDS+
   */
  fun populateSdsPlusMarkerForOffences(sentencesAndOffences: List<SentenceAndOffence>): List<SentenceAndOffenceWithReleaseArrangements> {
    log.info("Checking ${sentencesAndOffences.size} sentences for SDS+")
    val bookingIdToSentences = getMatchingSentencesToBookingId(sentencesAndOffences)
    val offencesToCheck = getOffenceCodesToCheckWithMO(bookingIdToSentences)
    val bookingToSentenceOffenceMap = mutableMapOf<Long, List<SentenceAndOffence>>()

    if (offencesToCheck.isNotEmpty()) {
      val moCheckResponses = manageOffencesService.getPcscMarkersForOffenceCodes(*offencesToCheck.toTypedArray()).associateBy { it.offenceCode }

      bookingIdToSentences.forEach {
        it.value
          .filter { sentenceAndOffence -> sentenceAndOffence.offence.offenceCode in offencesToCheck }
          .filter { sentenceAndOffence ->
            val offenderOffence = sentenceAndOffence.offence
            val moResponseForOffence = moCheckResponses[offenderOffence.offenceCode]
            val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffence)
            val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
            val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffence)

            checkIsSDSPlusAndSetOffenceIndicators(
              sentenceCalculationType,
              sentenceAndOffence,
              sevenYearsOrMore,
              moResponseForOffence,
              sentenceIsAfterPcsc,
              offenderOffence,
            )
          }
          .onEach { sentenceAndOffence -> sentenceAndOffence.offence.indicators = sentenceAndOffence.offence.indicators.plus(listOf(OffenderOffence.PCSC_SDS_PLUS)) }
          .forEach { sentenceAndOffence ->
            if (bookingToSentenceOffenceMap.contains(sentenceAndOffence.bookingId)) {
              bookingToSentenceOffenceMap[sentenceAndOffence.bookingId]?.plus(sentenceAndOffence)
            } else {
              bookingToSentenceOffenceMap[sentenceAndOffence.bookingId] = listOf(sentenceAndOffence)
            }
          }
      }
    }
    val sentencesWithSDSPlus = bookingToSentenceOffenceMap.map { it.value }.flatten()
    return sentencesAndOffences.map { SentenceAndOffenceWithReleaseArrangements(it, it in sentencesWithSDSPlus) }
  }

  private fun checkIsSDSPlusAndSetOffenceIndicators(
    sentenceCalculationType: SentenceCalculationType,
    sentenceAndOffence: SentenceAndOffence,
    sevenYearsOrMore: Boolean,
    moResponseForOffence: OffencePcscMarkers?,
    sentenceIsAfterPcsc: Boolean,
    offence: OffenderOffence,
  ): Boolean {
    var sdsPlusIdentified = false
    if (postPcscCalcTypes["SDS"]!!.contains(sentenceCalculationType) ||
      postPcscCalcTypes["DYOI"]!!.contains(sentenceCalculationType)
    ) {
      if (sentencedWithinOriginalSdsPlusWindow(sentenceAndOffence) && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListA == true) {
        offence.indicators = offence.indicators.plus(listOf(OffenderOffence.SCHEDULE_15_LIFE_INDICATOR))
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListD == true) {
        offence.indicators = offence.indicators.plus(listOf(OffenderOffence.PCSC_SDS_PLUS))
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && fourToUnderSeven(sentenceAndOffence) && moResponseForOffence?.pcscMarkers?.inListB == true) {
        offence.indicators = offence.indicators.plus(listOf(OffenderOffence.PCSC_SDS))
        sdsPlusIdentified = true
      }
    } else if (postPcscCalcTypes["S250"]!!.contains(sentenceCalculationType)) {
      if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListC == true) {
        offence.indicators = offence.indicators.plus(listOf(OffenderOffence.PCSC_SEC250))
        sdsPlusIdentified = true
      }
    }
    return sdsPlusIdentified
  }

  fun getOffenceCodesToCheckWithMO(bookingIdToSentences: Map<Long, List<SentenceAndOffence>>): List<String> {
    val offencesToCheck = bookingIdToSentences.map { (_, sentenceAndOffences) ->
      sentenceAndOffences.map { it.offence.offenceCode }
    }.flatten().distinct()
    return offencesToCheck
  }

  fun getMatchingSentencesToBookingId(sentencesAndOffences: List<SentenceAndOffence>): Map<Long, List<SentenceAndOffence>> {
    val bookingIdToSentences = sentencesAndOffences
      .filter { SentenceCalculationType.isSupported(it.sentenceCalculationType) }
      .filter { sentenceAndOffences ->
        val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffences.sentenceCalculationType)
        val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffences)
        val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffences)
        val sentencedWithinOriginalSdsWindow = sentencedWithinOriginalSdsPlusWindow(sentenceAndOffences)

        var matchFilter = false

        if (postPcscCalcTypes["SDS"]!!.contains(sentenceCalculationType) || postPcscCalcTypes["DYOI"]!!.contains(sentenceCalculationType)) {
          if (sentencedWithinOriginalSdsWindow && sevenYearsOrMore) {
            matchFilter = true
          } else if (sentenceIsAfterPcsc && overFourYearsSentenceLength(sentenceAndOffences)) {
            matchFilter = true
          }
        } else if (postPcscCalcTypes["S250"]!!.contains(sentenceCalculationType) && sentenceIsAfterPcsc && sevenYearsOrMore) {
          matchFilter = true
        }

        matchFilter
      }
      .groupBy { it.bookingId }
    return bookingIdToSentences
  }

  private fun sentencedAfterPcsc(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(
      ImportantDates.PCSC_COMMENCEMENT_DATE,
    )
  }

  private fun sentencedWithinOriginalSdsPlusWindow(sentence: SentenceAndOffence): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && !sentencedAfterPcsc(sentence)
  }

  private fun overFourYearsSentenceLength(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  private fun endOfSentence(sentence: SentenceAndOffence): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }

  private fun sevenYearsOrMore(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(sentence: SentenceAndOffence): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
