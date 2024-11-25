package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence

/**
 * Offence codes excluded from early release scheme as of trance3 commencement date
 */
val t3offenceCodes = listOf(
  "CJ15005",
  "CJ15013",
  "ST19001",
  "PH97003",
  "PH97005",
  "PH97003A",
  "PH97003B",
  "SE20005",
  "SE20006",
  "SE20012",
  "SE20012A",
  "PH97012",
  "COML025",
  "COML026",
)

fun offenceIsTrancheThreeAffected(
  sentence: CalculableSentence,
  trancheConfiguration: SDS40TrancheConfiguration,
): Boolean = sentence.offence.offenceCode in t3offenceCodes &&
  sentence.sentencedAt.isBefore(trancheConfiguration.trancheThreeCommencementDate)
