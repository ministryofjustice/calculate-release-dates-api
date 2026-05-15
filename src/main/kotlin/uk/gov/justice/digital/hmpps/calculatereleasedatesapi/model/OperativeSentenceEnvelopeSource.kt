package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class OperativeSentenceEnvelopeSource {
  NOMIS,
  CRDS,
}
