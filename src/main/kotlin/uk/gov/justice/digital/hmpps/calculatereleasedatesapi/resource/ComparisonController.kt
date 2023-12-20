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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.UserContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonPersonOverview
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ComparisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ComparisonInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.ComparisonService

@RestController
@RequestMapping("/comparison", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "comparison-controller", description = "Operations performing a comparison")
class ComparisonController(
  private val comparisonService: ComparisonService,
) {

  @PostMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "Create a record of a new calculation ",
    description = "This endpoint will create a new calculation and return the new calculation object",
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
    comparison: ComparisonInput,
    @RequestHeader("Authorization") token: String,
  ): Comparison {
    log.info("Request received to create a new Comparison -- $comparison")
    UserContext.setAuthToken(token)
    log.info("Set token {}", UserContext.getAuthToken())
    return comparisonService.create(comparison)
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER')")
  @ResponseBody
  @Operation(
    summary = "List all Comparisons performed using presets",
    description = "This endpoint will return all the comparisons for your caseload",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns a list of comparisons Comparison"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getComparisons(): List<ComparisonSummary> {
    log.info("Requested a list of Comparisons")
    return comparisonService.listComparisons()
  }

  @GetMapping(value = ["{comparisonReference}/count"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER')")
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
    log.info("Requested a count of the number of persons in a particular comparison")
    return comparisonService.getCountOfPersonsInComparisonByComparisonReference(comparisonReference)
  }

  @GetMapping(value = ["{comparisonReference}"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER')")
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
    log.info("Return the specific particular comparison")
    return comparisonService.getComparisonByComparisonReference(comparisonReference)
  }

  @GetMapping(value = ["{comparisonReference}/mismatch/{mismatchReference}"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATE_COMPARER')")
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
  fun getComparisonMismatchByShortReference(
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the comparison")
    @PathVariable("comparisonReference")
    comparisonReference: String,
    @Parameter(required = true, example = "A1B2C3D4", description = "The short reference of the mismatch")
    @PathVariable("mismatchReference")
    mismatchReference: String,
  ): ComparisonPersonOverview {
    return comparisonService.getComparisonPersonByShortReference(comparisonReference, mismatchReference)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
