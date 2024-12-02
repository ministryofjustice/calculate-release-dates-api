package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence

fun offenceIsTrancheThreeAffected(
  sentence: StandardDeterminateSentence,
  trancheConfiguration: SDS40TrancheConfiguration,
): Boolean = sentence.hasAnSDSEarlyReleaseExclusion.name.endsWith("_T3") &&
  sentence.sentencedAt.isBefore(trancheConfiguration.trancheThreeCommencementDate)
