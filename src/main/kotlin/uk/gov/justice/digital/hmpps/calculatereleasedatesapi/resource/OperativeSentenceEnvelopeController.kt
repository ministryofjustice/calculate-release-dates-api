package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import arrow.core.getOrElse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.NoActiveBookingException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OperativeSentenceEnvelopeService

@RestController
@RequestMapping("/operative-sentence-envelope", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "operative-sentence-envelope-controller", description = "APIs for analysing the sentence envelope of a prisoner")
class OperativeSentenceEnvelopeController(private val operativeSentenceEnvelopeService: OperativeSentenceEnvelopeService) {

  @GetMapping(value = ["/{prisonerId}"])
  @PreAuthorize("hasAnyRole('CALCULATE_RELEASE_DATES__SENTENCE_ENVELOPE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get the current operative sentence envelope for a prisoner",
    description = "Required role: CALCULATE_RELEASE_DATES__SENTENCE_ENVELOPE__RO\n\nDetermines the operative sentence envelope for a prisoner either using CRDS data if available or NOMIS if not.\n\nIndicators for SDS+ and recall are only available when the source is CRDS. ",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Operative sentence envelope data"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "Could not locate an appropriate source of data to determine the operative sentence envelope"),
    ],
  )
  fun getOperationalSentenceEnvelopeForPrisoner(
    @Parameter(required = true, example = "ABC123", description = "The id of the prisoner")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): OperativeSentenceEnvelope {
    val result = operativeSentenceEnvelopeService.operativeSentenceEnvelopeForPrisoner(prisonerId)
    return result.getOrElse { problemMessage -> throw NoActiveBookingException(problemMessage) }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
