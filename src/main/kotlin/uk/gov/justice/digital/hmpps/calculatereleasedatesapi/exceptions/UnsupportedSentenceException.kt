package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffences

class UnsupportedSentenceException(message: String, val sentenceAndOffences: List<SentenceAndOffences>) : Exception(message)
