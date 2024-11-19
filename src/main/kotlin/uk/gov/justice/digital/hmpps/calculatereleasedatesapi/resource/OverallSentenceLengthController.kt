package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthComparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OverallSentenceLengthRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.OverallSentenceLengthService

@RestController
@RequestMapping("/overall-sentence-length", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "overall-sentence-length-controller", description = "Controller to find overall sentence length details")
class OverallSentenceLengthController(
  private val overallSentenceLengthService: OverallSentenceLengthService,
) {

  @PostMapping
  @ResponseBody
  @PreAuthorize("hasAnyRole('CALCULATE_RELEASE_DATES__CALCULATE_RW', 'CALCULATE_RELEASE_DATES__CALCULATE_RO')")
  @Operation(
    summary = "Find overall sentence length comparison",
    description = "Compares the sentence durations to an overall sentence length",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns non friday release day"),
      ApiResponse(responseCode = "400", description = "Bad Request"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun compareOverallSentenceLength(
    @RequestBody
    overallSentenceLengthRequest: OverallSentenceLengthRequest,
  ): OverallSentenceLengthComparison {
    log.info("Request received to compare overall sentence length")
    return overallSentenceLengthService.compare(overallSentenceLengthRequest)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
