package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository

@RestController
@RequestMapping("/calculation-reasons", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "calculation-reason-controller", description = "Returns a list of active reasons for calculation")
class CalculationReasonController(
  private val calculationReasonRepository: CalculationReasonRepository,
) {

  @GetMapping("/")
  @PreAuthorize("hasAnyRole('SYSTEM_USER', 'RELEASE_DATES_CALCULATOR')")
  @ResponseBody
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Returns list of active reasons"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "No active calculation reasons were found"),
    ],
  )
  fun getActiveCalculationReasons(): List<CalculationReason> {
    log.info("Request for active calculation reasons received")
    return calculationReasonRepository.findAllByIsActiveTrueOrderByDisplayRankAsc()
  }
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
