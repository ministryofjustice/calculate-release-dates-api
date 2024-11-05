package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateTypes
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

class BookingHelperTest {

  private fun createSentenceChain(
    start: AbstractSentence,
    chain: MutableList<AbstractSentence>,
    sentencesByPrevious: Map<UUID, List<AbstractSentence>>,
    chains: MutableList<MutableList<AbstractSentence>> = mutableListOf(mutableListOf()),
  ) {
    val originalChain = chain.toMutableList()
    sentencesByPrevious[start.identifier]?.forEachIndexed { index, it ->
      if (index == 0) {
        chain.add(it)
        createSentenceChain(it, chain, sentencesByPrevious, chains)
      } else {
        // This sentence has two sentences consecutive to it. This is not allowed in practice, however it can happen
        // when a sentence in NOMIS has multiple offices, which means it becomes multiple sentences in our model.
        val chainCopy = originalChain.toMutableList()
        chains.add(chainCopy)
        chainCopy.add(it)
        createSentenceChain(it, chainCopy, sentencesByPrevious, chains)
      }
    }
  }

  fun createConsecutiveSentences(booking: Booking): Booking {
    val (baseSentences, consecutiveSentences) = booking.sentences.partition { it.consecutiveSentenceUUIDs.isEmpty() }
    val sentencesByPrevious = consecutiveSentences.groupBy {
      it.consecutiveSentenceUUIDs.first()
    }

    val chains: MutableList<MutableList<AbstractSentence>> = mutableListOf(mutableListOf())

    baseSentences.forEach {
      val chain: MutableList<AbstractSentence> = mutableListOf()
      chains.add(chain)
      chain.add(it)
      createSentenceChain(it, chain, sentencesByPrevious, chains)
    }

    booking.consecutiveSentences = chains.filter { it.size > 1 }
      .map {
        val cs = ConsecutiveSentence(it)
        cs.sentenceCalculation = BookingHelperTest.SENTENCE_CALCULATION
        cs.releaseDateTypes = ReleaseDateTypes(listOf(ReleaseDateType.TUSED), it[0], booking.offender)
        cs
      }
    booking.consecutiveSentences.forEach { it.sentenceCalculation }

    booking.sentenceGroups = emptyList()
    return booking
  }

  companion object {
    const val BOOKING_ID = 100091L
    private val OFFENCE = Offence(LocalDate.of(2020, 1, 1))
    private val FIRST_MAY_2018: LocalDate = LocalDate.of(2018, 5, 1)
    private val ONE_DAY_DURATION = Duration(mapOf(ChronoUnit.DAYS to 1L))
    private val STANDARD_SENTENCE = StandardDeterminateSentence(
      offence = OFFENCE,
      duration = BookingHelperTest.ONE_DAY_DURATION,
      sentencedAt = LocalDate.of(2020, 1, 1),
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    val SENTENCE_CALCULATION = SentenceCalculation(
      STANDARD_SENTENCE,
      3,
      4.0,
      4,
      4,
      FIRST_MAY_2018,
      FIRST_MAY_2018,
      FIRST_MAY_2018,
      1,
      FIRST_MAY_2018,
      false,
      Adjustments(
        mutableMapOf(
          AdjustmentType.REMAND to mutableListOf(
            Adjustment(
              numberOfDays = 1,
              appliesToSentencesFrom = FIRST_MAY_2018,
            ),
          ),
        ),
      ),
      FIRST_MAY_2018,
    )
  }
}
