package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.earlyrelease.config

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculableSentence

interface SDSLegislationWithTranches :
  SDSLegislation,
  LegislationWithTranches {
  fun isSentenceSubjectToTraches(sentence: CalculableSentence): Boolean
}
