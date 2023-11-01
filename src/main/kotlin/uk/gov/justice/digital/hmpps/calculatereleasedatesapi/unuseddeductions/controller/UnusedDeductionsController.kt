package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.CalculationController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.unuseddeductions.service.UnusedDeductionsService

@RestController
@RequestMapping("/unused-deductions", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "unused-deductions-controller", description = "Operations involving a calculating unused deductions")
class UnusedDeductionsController(
    val unusedDeductionsService: UnusedDeductionsService
) {

    @PostMapping(value = ["/{prisonerId}"])
    @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
    @ResponseBody
    @Operation(
        summary = "Calculate unused deductions and push the data back into adjustments api.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Calculated"),
            ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
            ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
        ],
    )
    fun calculate(
        @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
        @PathVariable("prisonerId")
        prisonerId: String,
    ) {
        CalculationController.log.info("Request received to calculate unused deductions for $prisonerId")
        unusedDeductionsService.handleUnusedDeductionRequest(prisonerId)
    }
}