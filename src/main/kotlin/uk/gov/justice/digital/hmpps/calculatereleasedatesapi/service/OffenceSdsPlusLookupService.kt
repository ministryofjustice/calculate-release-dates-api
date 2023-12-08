package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.isAfterOrEqualTo
import java.time.LocalDate
import java.time.Period
import java.util.*

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
   * @param sentencesAndOffences - A list of [SentenceAndOffences] to process for SDS+
   * @return matchingSentenceMap - A map of sentences with offences flagged as SDS+.
   */
  fun populateSdsPlusMarkerForOffences(sentencesAndOffences: List<SentenceAndOffences>): Map<SentenceAndOffences, List<OffenderOffence>> {
    val bookingIdToSentences = getMatchingSentencesToBookingId(sentencesAndOffences)
    val offencesToCheck = getOffenceCodesToCheckWithMO(bookingIdToSentences)
    val matchingSentenceMap = mutableMapOf<SentenceAndOffences, List<OffenderOffence>>()

    if (offencesToCheck.isNotEmpty()) {
      val moCheckResponses = manageOffencesService.getPcscMarkersForOffenceCodes(*offencesToCheck.toTypedArray()).associateBy { it.offenceCode }

      bookingIdToSentences.forEach {
        it.value
          .forEach { sentenceAndOffence ->
            sentenceAndOffence.offences
              .filter { offenderOffence -> offencesToCheck.contains(offenderOffence.offenceCode) }
              .filter { offenderOffence ->
                val moResponseForOffence = moCheckResponses[offenderOffence.offenceCode]
                val sentenceIsAfterPcsc = sentencedAfterPcsc(sentenceAndOffence)
                val sentenceCalculationType = SentenceCalculationType.from(sentenceAndOffence.sentenceCalculationType)
                val sevenYearsOrMore = sevenYearsOrMore(sentenceAndOffence)

                return@filter isOffenceSdsPlus(sentenceCalculationType, sentenceAndOffence, sevenYearsOrMore, moResponseForOffence, sentenceIsAfterPcsc)
              }
              .forEach { offence ->
                offence.indicators = offence.indicators.plus(listOf(OffenderOffence.PCSC_SDS_PLUS))

                if (matchingSentenceMap.contains(sentenceAndOffence)) {
                  matchingSentenceMap.get(sentenceAndOffence)?.plus(offence)
                } else {
                  matchingSentenceMap[sentenceAndOffence] = listOf(offence)
                }
              }
          }
      }
    }
    return matchingSentenceMap
  }

  private fun isOffenceSdsPlus(sentenceCalculationType: SentenceCalculationType, sentenceAndOffence: SentenceAndOffences, sevenYearsOrMore: Boolean, moResponseForOffence: OffencePcscMarkers?, sentenceIsAfterPcsc: Boolean): Boolean {
    var sdsPlusIdentified = false
    if (postPcscCalcTypes["SDS"]!!.contains(sentenceCalculationType) ||
      postPcscCalcTypes["DYOI"]!!.contains(sentenceCalculationType)
    ) {
      if (sentencedWithinOriginalSdsPlusWindow(sentenceAndOffence) && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListA == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListD == true) {
        sdsPlusIdentified = true
      } else if (sentenceIsAfterPcsc && fourToUnderSeven(sentenceAndOffence) && moResponseForOffence?.pcscMarkers?.inListB == true) {
        sdsPlusIdentified = true
      }
    } else if (postPcscCalcTypes["S250"]!!.contains(sentenceCalculationType)) {
      if (sentenceIsAfterPcsc && sevenYearsOrMore && moResponseForOffence?.pcscMarkers?.inListC == true) {
        sdsPlusIdentified = true
      }
    }
    return sdsPlusIdentified
  }

  fun getOffenceCodesToCheckWithMO(bookingIdToSentences: Map<Long, List<SentenceAndOffences>>): List<String> {
    val offencesToCheck = bookingIdToSentences.map {
      it.value.flatMap { offenceList -> offenceList.offences.map { offence -> offence.offenceCode } }
    }.flatten().distinct()
    return offencesToCheck
  }

  fun getMatchingSentencesToBookingId(sentencesAndOffences: List<SentenceAndOffences>): Map<Long, List<SentenceAndOffences>> {
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

        return@filter matchFilter
      }
      .groupBy { it.bookingId }
    return bookingIdToSentences
  }

  private fun sentencedAfterPcsc(sentence: SentenceAndOffences): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(
      ImportantDates.PCSC_COMMENCEMENT_DATE,
    )
  }

  private fun sentencedWithinOriginalSdsPlusWindow(sentence: SentenceAndOffences): Boolean {
    return sentence.sentenceDate.isAfterOrEqualTo(ImportantDates.SDS_PLUS_COMMENCEMENT_DATE) && !sentencedAfterPcsc(sentence)
  }

  private fun overFourYearsSentenceLength(sentence: SentenceAndOffences): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears)
  }

  private fun endOfSentence(sentence: SentenceAndOffences): LocalDate {
    val duration =
      Period.of(sentence.terms[0].years, sentence.terms[0].months, sentence.terms[0].weeks * 7 + sentence.terms[0].days)
    return sentence.sentenceDate.plus(duration)
  }

  private fun sevenYearsOrMore(sentence: SentenceAndOffences): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfSevenYears)
  }

  private fun fourToUnderSeven(sentence: SentenceAndOffences): Boolean {
    val endOfSentence = endOfSentence(sentence)
    val endOfFourYears = sentence.sentenceDate.plusYears(4)
    val endOfSevenYears = sentence.sentenceDate.plusYears(7)
    return endOfSentence.isAfterOrEqualTo(endOfFourYears) && endOfSentence.isBefore(endOfSevenYears)
  }
}
