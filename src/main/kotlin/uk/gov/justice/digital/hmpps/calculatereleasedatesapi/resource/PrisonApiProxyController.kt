package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalyzedSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.SentenceAndOffenceService

@RestController
@RequestMapping("/sentence-and-offence-information", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "specialist-support-controller", description = "Operations for specialist support")
class PrisonApiProxyController(
  val sentenceAndOffenceService: SentenceAndOffenceService,
) {
  @GetMapping(value = ["/{bookingId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @Operation(
    summary = "Get sentence and offence information",
    description = "This endpoint will return a response model which lists sentence and offence information. It will notify if there have been any changed since last calculation",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a List<AnalyzedSentenceAndOffences"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun storeGenuineOverride(@PathVariable bookingId: Long): List<AnalyzedSentenceAndOffences> {
    return sentenceAndOffenceService.getSentencesAndOffences(bookingId)
  }
}
