package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util

class ConsecutiveSentenceUtil {

  companion object {
    fun <S, K : Any> createConsecutiveChains(
      sentences: List<S>,
      keyMapper: (s: S) -> K,
      consecToMapper: (s: S) -> K?,
    ): List<List<S>> {
      val (baseSentences, consecutiveSentenceParts) = sentences.partition { consecToMapper(it) == null }
      val sentencesByPrevious = consecutiveSentenceParts.groupBy {
        consecToMapper(it)!!
      }

      val chains: MutableList<MutableList<S>> = mutableListOf(mutableListOf())

      baseSentences.forEach {
        val chain: MutableList<S> = mutableListOf()
        chains.add(chain)
        chain.add(it)
        createSentenceChain(it, chain, sentencesByPrevious, chains, keyMapper)
      }

      return chains
    }

    private fun <S, K> createSentenceChain(
      start: S,
      chain: MutableList<S>,
      sentencesByPrevious: Map<K, List<S>>,
      chains: MutableList<MutableList<S>> = mutableListOf(mutableListOf()),
      keyMapper: (s: S) -> K,
    ) {
      val originalChain = chain.toMutableList()
      sentencesByPrevious[keyMapper(start)]?.forEachIndexed { index, it ->
        if (index == 0) {
          chain.add(it)
          createSentenceChain(it, chain, sentencesByPrevious, chains, keyMapper)
        } else {
          val chainCopy = originalChain.toMutableList()
          chains.add(chainCopy)
          chainCopy.add(it)
          createSentenceChain(it, chainCopy, sentencesByPrevious, chains, keyMapper)
        }
      }
    }
  }
}
