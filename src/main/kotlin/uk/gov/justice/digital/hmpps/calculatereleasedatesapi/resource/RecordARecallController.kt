package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecisionResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallValidationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RecordARecallDecisionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.RecordARecallService

@RestController
@Tag(name = "record-a-recall-controller", description = "Operations involving a calculation")
class RecordARecallController(
  private val recordARecallService: RecordARecallService,
  private val recordARecallDecisionService: RecordARecallDecisionService,
) {
  @PostMapping(value = ["/record-a-recall/{prisonerId}"])
  @PreAuthorize("hasAnyRole('RECORD_A_RECALL', 'CALCULATE_RELEASE_DATES__RECALL__CALCULATE__RW')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - used explicitly by the record-a-recall service, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a transitory calculation that will not be published to NOMIS",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "422", description = "Unprocessable request, the existing data cannot be used to perform a calculation"),
    ],
  )
  fun calculateForRecall(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): RecordARecallResult {
    log.info("Request received to calculate release dates for a recall of $prisonerId")
    return recordARecallService.calculateAndValidateForRecordARecall(prisonerId)
  }

  @PostMapping(value = ["/record-a-recall/{prisonerId}/decision"])
  @PreAuthorize("hasAnyRole('RECORD_A_RECALL', 'CALCULATE_RELEASE_DATES__RECALL__CALCULATE__RW')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - used explicitly by the record-a-recall service, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a transitory calculation that will not be published to NOMIS",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "422", description = "Unprocessable request, the existing data cannot be used to perform a calculation"),
    ],
  )
  fun makeRecallDecision(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
    @RequestBody
    recordARecallRequest: RecordARecallRequest,
  ): RecordARecallDecisionResult {
    log.info("Request received to make calculate release dates decision for a recall of $prisonerId")
    return recordARecallDecisionService.makeRecallDecision(prisonerId, recordARecallRequest)
  }

  @GetMapping(value = ["/record-a-recall/{prisonerId}/validate"])
  @PreAuthorize("hasAnyRole('RECORD_A_RECALL', 'CALCULATE_RELEASE_DATES__RECALL__CALCULATE__RW')")
  @ResponseBody
  @Operation(
    summary = "Calculate release dates for a prisoner - used explicitly by the record-a-recall service, this does not publish to NOMIS",
    description = "This endpoint will calculate release dates based on a prisoners latest booking - this is a transitory calculation that will not be published to NOMIS",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns calculated dates"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "422", description = "Unprocessable request, the existing data cannot be used to perform a calculation"),
    ],
  )
  fun validate(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerId")
    prisonerId: String,
  ): RecordARecallValidationResult {
    log.info("Request received to validate calculate release dates for a recall of $prisonerId")
    return recordARecallDecisionService.validate(prisonerId)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
