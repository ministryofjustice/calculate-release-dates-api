package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOptions
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DtoSingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermed
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.util.UUID

@Service
class BookingCalculationService(
  val sentenceIdentificationService: SentenceIdentificationService,
) {

  fun identify(booking: Booking, options: CalculationOptions) {
    for (sentence in booking.sentences) {
      sentenceIdentificationService.identify(sentence, booking.offender, options)
    }
  }

  private fun allSdsBeforeLaspo(booking: Booking): Boolean = booking.sentences.all { it is StandardDeterminateSentence && it.isBeforeCJAAndLASPO() }

  private fun allDtos(booking: Booking): Boolean = booking.sentences.all { it is DetentionAndTrainingOrderSentence }

  fun getSentencesToCalculate(booking: Booking, options: CalculationOptions): List<CalculableSentence> {
    val consecutiveSentences = createConsecutiveSentences(booking, options)

    val singleTermed = createSingleTermSentences(booking, options)

    return getAllExtractableSentences(booking.sentences, singleTermed, consecutiveSentences)
  }

  fun createSingleTermSentences(booking: Booking, options: CalculationOptions): SingleTermed? {
    if (booking.sentences.size > 1 &&
      (allSdsBeforeLaspo(booking) || allDtos(booking)) &&
      booking.sentences.all { it.consecutiveSentenceUUIDs.isEmpty() } &&
      booking.sentences.minOf { it.sentencedAt } != booking.sentences.maxOf { it.sentencedAt } &&
      booking.sentences.all { !it.isRecall() }
    ) {
      val sentence = if (booking.sentences.any { it is DetentionAndTrainingOrderSentence }) {
        DtoSingleTermSentence(booking.sentences)
      } else {
        SingleTermSentence(booking.sentences)
      }
      sentenceIdentificationService.identify(sentence, booking.offender, options)
      return sentence
    }
    return null
  }

  fun createConsecutiveSentences(booking: Booking, options: CalculationOptions): List<ConsecutiveSentence> {
    val (baseSentences, consecutiveSentenceParts) = booking.sentences.partition { it.consecutiveSentenceUUIDs.isEmpty() }
    val sentencesByPrevious = consecutiveSentenceParts.groupBy {
      it.consecutiveSentenceUUIDs.first()
    }

    val chains: MutableList<MutableList<AbstractSentence>> = mutableListOf(mutableListOf())

    baseSentences.forEach {
      val chain: MutableList<AbstractSentence> = mutableListOf()
      chains.add(chain)
      chain.add(it)
      createSentenceChain(it, chain, sentencesByPrevious, chains)
    }

    val consecutiveSentences = collapseDuplicateConsecutiveSentences(
      chains.filter { it.size > 1 }
        .map { ConsecutiveSentence(it) },
    )

    consecutiveSentences.forEach {
      sentenceIdentificationService.identify(it, booking.offender, options)
    }
    return consecutiveSentences
  }

  /*
    The service created a consecutive sentence for every possible combination of offence.
     It does this in case a sentence within the chain has multiple offences, which may have different release conditions
     However here we reduce the list by any duplicated offences with the same release conditions, to improve calculation time.
   */
  private fun collapseDuplicateConsecutiveSentences(consecutiveSentences: List<ConsecutiveSentence>): List<ConsecutiveSentence> {
    return consecutiveSentences.distinctBy {
      it.orderedSentences.joinToString { sentence -> "${sentence.sentencedAt}${sentence.identificationTrack}${sentence.totalDuration()}${sentence.javaClass}" }
    }
  }

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

  private fun getAllExtractableSentences(
    sentences: List<AbstractSentence>,
    singleTermed: SingleTermed?,
    consecutiveSentences: List<ConsecutiveSentence>,
  ): List<CalculableSentence> {
    val extractableSentences: MutableList<CalculableSentence> = consecutiveSentences.toMutableList()
    if (singleTermed != null) {
      extractableSentences.add(singleTermed)
    }
    sentences.forEach {
      if (consecutiveSentences.none { consecutive -> consecutive.orderedSentences.contains(it) } &&
        singleTermed?.standardSentences?.contains(it) != true
      ) {
        extractableSentences.add(it)
      }
    }
    return extractableSentences.toList()
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
