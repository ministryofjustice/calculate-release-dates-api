package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceSummary

@Component
  class PrisonApiSourceDataConverter : Converter<SentenceSummary, PrisonApiSourceData> {
    override fun convert(source: SentenceSummary): PrisonApiSourceData? {
      var sentencesAndOffences = source.latestPrisonTerm?.courtSentences
      var prisonerDetails = PrisonerDetails(source.latestPrisonTerm.bookingId!!, source.prisonerNumber!!, "", "", , listOf(), null)
      PrisonApiSourceData(sentencesAndOffences, prisonerDetails,  )
    }
  }
