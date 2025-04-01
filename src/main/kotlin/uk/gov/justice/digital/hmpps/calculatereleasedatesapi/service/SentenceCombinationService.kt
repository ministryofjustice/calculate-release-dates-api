package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import arrow.core.left
import arrow.core.leftNel
import arrow.core.right
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AbstractSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetentionAndTrainingOrderSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DtoSingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SingleTermed
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import java.util.UUID

@Service
class SentenceCombinationService(
  val sentenceIdentificationService: SentenceIdentificationService,
) {

  fun getSentencesToCalculate(sentences: List<CalculableSentence>, offender: Offender): List<CalculableSentence> {
    val singleSentences = sentences.flatMap { it.sentenceParts() }
    val consecutiveSentences = createConsecutiveSentences(singleSentences, offender)

    val singleTermed = createSingleTermSentences(singleSentences, offender)

    return getAllExtractableSentences(singleSentences, singleTermed, consecutiveSentences)
  }

  private fun allSdsBeforeLaspo(sentences: List<AbstractSentence>): Boolean = sentences.all { it is StandardDeterminateSentence && it.isBeforeCJAAndLASPO() }

  private fun allDtos(sentences: List<AbstractSentence>,): Boolean = sentences.all { it is DetentionAndTrainingOrderSentence }

  fun createSingleTermSentences(sentences: List<AbstractSentence>, offender: Offender): SingleTermed? {
    if (sentences.size > 1 &&
      (allSdsBeforeLaspo(sentences) || allDtos(sentences)) &&
      sentences.all { it.consecutiveSentenceUUIDs.isEmpty() } &&
      sentences.minOf { it.sentencedAt } != sentences.maxOf { it.sentencedAt } &&
      sentences.all { !it.isRecall() }
    ) {
      val sentence = if (sentences.any { it is DetentionAndTrainingOrderSentence }) {
        DtoSingleTermSentence(sentences)
      } else {
        SingleTermSentence(sentences)
      }
      sentenceIdentificationService.identify(sentence, offender)
      return sentence
    }
    return null
  }

  fun createConsecutiveSentences(sentences: List<AbstractSentence>, offender: Offender): List<ConsecutiveSentence> {
    val (baseSentences, consecutiveSentenceParts) = sentences.partition { it.consecutiveSentenceUUIDs.isEmpty() }
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
      sentenceIdentificationService.identify(it, offender)
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
      it.orderedSentences.joinToString { sentence -> "${sentence.sentencedAt}${sentence.identificationTrack}${sentence.totalDuration()}${sentence.javaClass}${sentence.recallType}" }
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
