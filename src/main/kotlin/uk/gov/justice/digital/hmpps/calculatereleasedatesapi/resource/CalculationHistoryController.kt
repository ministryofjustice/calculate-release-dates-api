package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.HistoricCalculationSummaryPage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PageRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.HistoricCalculationsService

@RestController
@RequestMapping("/calculation-history", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "calculation-history-controller", description = "Operations to handle the calculation history for a prisoner")
class CalculationHistoryController(
  private val historicCalculationsService: HistoricCalculationsService,
) {

  @GetMapping(value = ["/{nomsId}"])
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__CALCULATE__RW', 'CALCULATE_RELEASE_DATES__CALCULATE__RO')")
  @ResponseBody
  @Operation(
    summary = "Get a page of historic calculations for a prisoner",
    description = "This endpoint will return a list of calculations performed for a given prisoner based the on the page number and page size requested. The results are ordered with the most recent calculation first.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns historic calculations"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "This prisoner id does not exist"),
    ],
  )
  fun getHistoricCalculationSummaryPage(
    @Parameter(required = true, example = "AD123A", description = "The nomsId of the prisoner")
    @PathVariable("nomsId")
    nomsId: String,
    @Parameter(required = true, example = "1", description = "The number of the page to load with 1 being the first page")
    page: Int,
    @Parameter(required = true, example = "10", description = "The number of items to load in the page")
    size: Int,
  ): HistoricCalculationSummaryPage {
    log.info("Request received for calculation history page {} with size {} for nomsId {}", page, size, nomsId)
    return historicCalculationsService.getHistoricCalculationSummaryPage(nomsId, PageRequest(page, size))
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
