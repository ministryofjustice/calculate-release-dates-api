package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SourceType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.LatestCalculationService
import java.time.LocalDate

@RestController
@RequestMapping("/person", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "consumer-controller", description = "Release dates for one prisoner")
class ConsumerController(
  private val latestCalculationService: LatestCalculationService,
) {
  @GetMapping("/{prisonerNumber}/release-dates")
  @ResponseBody
  @Operation(
    operationId = "getReleaseDates",
    summary = "Release dates for one prisoner",
    description = "Returns individual release-date types for the prisoner's latest booking or period of custody.\n\nInitially, data is sourced from NOMIS and decorated by CRDS. The intention is to move to CRDS-only sourcing in the coming months, without changing the consumer contract.\n\nDoes not return derived dates or date combinations. Confirmed Release Date is excluded because it belongs to a scheduled external movement (a gate movement) and is not produced by CRDS.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Individual release dates for the prisoner's latest booking or period of custody. A known prisoner with no recorded dates is returned with an empty dates list."),
      ApiResponse(responseCode = "400", description = "Malformed prisoner number"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
      ApiResponse(responseCode = "404", description = "Prisoner not known"),
    ],
  )
  fun getReleaseDates(
    @Parameter(required = true, example = "A1234AB", description = "The prisoners ID (aka nomsId)")
    @PathVariable("prisonerNumber")
    prisonerNumber: String,
  ): ReleaseDates {
    log.info("Request received to get release dates for prisoner $prisonerNumber")
    val result = latestCalculationService.latestCalculationForPrisoner(prisonerNumber)
    return ReleaseDates(
      "",
      ReleaseDatesSource(SourceType.BOOKING, 123456),
      listOf(ReleaseDate(LocalDate.now(), ReleaseDateType.SED)),
    )
  }

  @PostMapping("/release-dates")
  @ResponseBody
  @Operation(
    operationId = "getReleaseDatesForPrisoner",
    summary = "Release dates for many prisoners",
    description = "POST avoids placing the bulk list of prisoner numbers in the URL. Request bodies must not be logged with prisoner numbers exposed.\n\nInitially, data is sourced from NOMIS and decorated by CRDS. This will initially be slower than the intended CRDS-only implementation. The intention is to move to CRDS-only sourcing in the coming months, without changing the consumer contract.\n\nReturns individual release-date types only. Does not return derived dates or date combinations. Confirmed Release Date is excluded because it belongs to a scheduled external movement (a gate movement) and is not produced by CRDS.\n\nThe response contains a list of release-date results. Each known prisoner appears once, identified by prisonerNumber.\n\nPrisoners that are not known are omitted from releaseDates and listed in notFound. Duplicate prisoner numbers in the request are collapsed.\n\nResponse ordering has no business meaning. Consumers must match results using prisonerNumber, not their position in the list.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Individual release dates for the requested prisoners. Known prisoners with no recorded dates are included with an empty dates list."),
      ApiResponse(responseCode = "400", description = "Empty list, too many prisoner numbers, or a malformed prisoner number."),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an appropriate role"),
    ],
  )
  fun getReleaseDatesForPrisoner(): List<ReleaseDates> {
    log.info("Request received to get release dates for prisoner")
    return listOf(
      ReleaseDates(
        "",
        ReleaseDatesSource(SourceType.BOOKING, 123456),
        listOf(ReleaseDate(LocalDate.now(), ReleaseDateType.SED)),
      )
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
