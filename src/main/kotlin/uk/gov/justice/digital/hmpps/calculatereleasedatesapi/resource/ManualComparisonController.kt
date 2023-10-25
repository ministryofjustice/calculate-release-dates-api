package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ManualComparisonService

@RestController
@RequestMapping("/comparison/manual", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "manual-comparison-controller", description = "Operations performing a manual comparison")
class ManualComparisonController(
  private val manualComparisonService: ManualComparisonService,
) {

  @PostMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @Operation(
    summary = "Create a record of a new manual calculation ",
    description = "This endpoint will create a new manual comparison and return the new manual comparison object",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a new Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun createComparison(
    @RequestBody
    manualComparison: ManualComparisonInput,
  ): Comparison {
    ComparisonController.log.info("Request received to create a new Comparison -- $manualComparison")
    return manualComparisonService.create(manualComparison)
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "List all comparisons which were performed manually",
    description = "This endpoint will return all of the manually performed calculations recorded in the service. This is not limited by caseload, but requires the MANUAL_COMPARER role.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getManualComparisons(): List<ComparisonSummary> {
    ComparisonController.log.info("Requested a list of manual Comparisons")
    return manualComparisonService.listManual()
  }

  @GetMapping(value = ["{comparisonReference}/count"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Returns a count of the number of people compared for a particular caseload",
    description = "This endpoint will count all the people associated to a specific comparison",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getCountOfPersonsInComparison(
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the comparison")
    @PathVariable("comparisonReference")
    comparisonReference: String,
  ): Long {
    ComparisonController.log.info("Requested a count of the number of persons in a particular comparison")
    return manualComparisonService.getCountOfPersonsInComparisonByComparisonReference(comparisonReference)
  }

  @GetMapping(value = ["{comparisonReference}"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Returns the people for a particular caseload",
    description = "This endpoint return the people associated to a specific comparison",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getComparisonByShortReference(
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the comparison")
    @PathVariable("comparisonReference")
    comparisonReference: String,
  ): ComparisonOverview {
    ComparisonController.log.info("Return the specific particular comparison")
    return manualComparisonService.getComparisonByComparisonReference(comparisonReference)
  }

  @GetMapping(value = ["{comparisonReference}/mismatch/{mismatchReference}"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_MANUAL_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Returns the mismatch for a particular comparison",
    description = "This endpoint return the mismatch for a particular comparison",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a details of a comparison mismatch"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getManualComparisonMismatchByShortReference(
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the comparison")
    @PathVariable("comparisonReference")
    comparisonReference: String,
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the mismatch")
    @PathVariable("mismatchReference")
    mismatchReference: String,
  ): ComparisonPersonOverview {
    return manualComparisonService.getComparisonPersonByShortReference(comparisonReference, mismatchReference)
  }
}
