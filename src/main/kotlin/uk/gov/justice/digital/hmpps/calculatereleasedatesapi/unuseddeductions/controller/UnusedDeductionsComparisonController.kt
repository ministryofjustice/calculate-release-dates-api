package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.entity.UnusedDeductionsComparisonOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonPersonDetail
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.model.UnusedDeductionsComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service.UnusedDeductionsComparisonService

@RestController
@RequestMapping("/unused-deductions/comparison", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "unused-deductions-comparison-controller", description = "Bulk comparison of existing unused deductions adjustments against a fresh calculation")
class UnusedDeductionsComparisonController(
  private val comparisonService: UnusedDeductionsComparisonService,
) {

  @PostMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER', 'CALCULATE_RELEASE_DATES__ADMIN__RW')")
  @ResponseBody
  @Operation(
    summary = "Start an unused deductions comparison",
    description = "Runs for a manual list of prisoners, a single prison or all prisons",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "The newly created comparison"),
      ApiResponse(responseCode = "400", description = "No prisoners or prison supplied"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun create(
    @RequestBody input: UnusedDeductionsComparisonInput,
    @RequestHeader("Authorization") token: String,
  ): UnusedDeductionsComparisonSummary {
    UserContext.setAuthToken(token)
    return comparisonService.create(input, token)
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER', 'CALCULATE_RELEASE_DATES__ADMIN__RW', 'CALCULATE_RELEASE_DATES__ADMIN__RO')")
  @ResponseBody
  @Operation(summary = "List unused deductions comparisons")
  fun list(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "10") size: Int,
  ): UnusedDeductionsComparisonPage = comparisonService.list(page, size)

  @GetMapping("/{comparisonReference}")
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER', 'CALCULATE_RELEASE_DATES__ADMIN__RW', 'CALCULATE_RELEASE_DATES__ADMIN__RO')")
  @ResponseBody
  @Operation(summary = "Get an unused deductions comparison and the result for each person")
  fun get(
    @PathVariable comparisonReference: String,
    @RequestParam(required = false) outcome: UnusedDeductionsComparisonOutcome?,
    @RequestParam(required = false) prisonerId: String?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "50") size: Int,
  ): UnusedDeductionsComparisonOverview = comparisonService.get(comparisonReference, outcome, prisonerId, page, size)

  @GetMapping("/{comparisonReference}/person/{personReference}")
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER', 'ROLE_RELEASE_DATE_MANUAL_COMPARER', 'CALCULATE_RELEASE_DATES__ADMIN__RW', 'CALCULATE_RELEASE_DATES__ADMIN__RO')")
  @ResponseBody
  @Operation(summary = "Get the detail of one person in an unused deductions comparison, including the adjustment inputs")
  fun getPerson(
    @PathVariable comparisonReference: String,
    @PathVariable personReference: String,
  ): UnusedDeductionsComparisonPersonDetail = comparisonService.getPerson(comparisonReference, personReference)
}
