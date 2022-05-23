package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExtendedDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.util.UUID

@Service
class BookingCalculationService(
  val sentenceCalculationService: SentenceCalculationService,
  val sentenceIdentificationService: SentenceIdentificationService
) {

  fun identify(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender)
    }
    return booking
  }

  fun calculate(booking: Booking): Booking {
    for (sentence in booking.sentences) {
      sentenceCalculationService.calculate(sentence, booking)
    }
    return booking
  }

  fun createSingleTermSentences(booking: Booking): Booking {
    if (booking.sentences.size > 1 &&
      booking.sentences.all { it.identificationTrack == SDS_BEFORE_CJA_LASPO && it is AbstractSentence && it.consecutiveSentenceUUIDs.isEmpty() } &&
      booking.sentences.minOf { it.sentencedAt } != booking.sentences.maxOf { it.sentencedAt } &&
      booking.sentences.all { !it.isRecall() }
    ) {
      booking.singleTermSentence = SingleTermSentence(booking.sentences.map { it as StandardDeterminateSentence })
      sentenceIdentificationService.identify(booking.singleTermSentence!!, booking.offender)
      sentenceCalculationService.calculate(booking.singleTermSentence!!, booking)
      log.info(booking.singleTermSentence!!.buildString())
    }
    return booking
  }

  fun createConsecutiveSentences(booking: Booking): Booking {
    val (baseSentences, consecutiveSentences) = booking.sentences.map { it as AbstractSentence }.partition { it.consecutiveSentenceUUIDs.isEmpty() }
    val sentencesByPrevious = consecutiveSentences.groupBy {
      it as AbstractSentence
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
      .map { it ->
        if (it[0] is StandardDeterminateSentence) {
          StandardDeterminateConsecutiveSentence(it.map { it as StandardDeterminateSentence })
        } else {
          ExtendedDeterminateConsecutiveSentence(it.map { it as ExtendedDeterminateSentence })
        }
      }

    booking.consecutiveSentences.forEach {
      sentenceIdentificationService.identify(it, booking.offender)
      sentenceCalculationService.calculate(it, booking)
      log.info(it.buildString())
    }
    return booking
  }

  private fun createSentenceChain(
    start: AbstractSentence,
    chain: MutableList<AbstractSentence>,
    sentencesByPrevious: Map<UUID, List<AbstractSentence>>,
    chains: MutableList<MutableList<AbstractSentence>> = mutableListOf(mutableListOf())
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

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
