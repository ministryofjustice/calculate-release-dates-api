package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AgencySwitchUpdateResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.AgencySwitch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.AgencySwitchService

@RestController
@RequestMapping("/feature-toggle", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "feature-toggle", description = "Feature toggle related APIs")
@Validated
class PrisonFeatureToggleController(
  private val agencySwitchService: AgencySwitchService,
  @param:Value("\${prisonsWithNomisCalcDisabled}")
  private val prisonsWithNomisCalcDisabled: Set<String>,
) {

  @GetMapping(value = ["/nomis-calc-disabled"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__ADMIN__RW', 'CALCULATE_RELEASE_DATES__ADMIN__RO')")
  @ResponseBody
  @Operation(
    summary = "Checks which prisons currently have NOMIS calc disabled",
    description = "Return a list of prisons with NOMIS calc disabled",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "A list of prisons with NOMIS calc disabled"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun getNomisCalcDisabled(): List<Agency> = agencySwitchService.getAgenciesWithSwitchOn(AgencySwitch.SENTENCE_CALC)

  @PostMapping(value = ["/nomis-calc-disabled"])
  @PreAuthorize("hasAnyRole('ROLE_RELEASE_DATES_CALCULATOR', 'CALCULATE_RELEASE_DATES__ADMIN__RW')")
  @ResponseBody
  @Operation(
    summary = "Add or remove the SENTENCE_CALC agency switch so that it matches the prisons in CRDS config",
    description = "Add or remove the SENTENCE_CALC agency switch so that it matches the prisons in CRDS config and return the current list of activated prisons. SENTENCE_CALC being on means NOMIS calc is disabled.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "An updated list of prisons with NOMIS calc disabled"),
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
    ],
  )
  fun updateNomisCalcDisabled(): AgencySwitchUpdateResult = agencySwitchService.setSwitchForAgencies(AgencySwitch.SENTENCE_CALC, prisonsWithNomisCalcDisabled)
}
