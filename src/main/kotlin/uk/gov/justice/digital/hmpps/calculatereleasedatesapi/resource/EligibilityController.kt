package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.eligibility.ErsedEligibilityService

@RestController
@RequestMapping("/eligibility", produces = [MediaType.APPLICATION_JSON_VALUE])
class EligibilityController(private val ersedEligibilityService: ErsedEligibilityService) {

  @GetMapping(value = ["/{bookingId}/ersed"])
  fun ersedEligibility(
    @Parameter(required = true, example = "100001", description = "The booking ID to check against")
    @PathVariable("bookingId")
    bookingId: Long,
  ): ErsedEligibilityService.ErsedEligibility = ersedEligibilityService.sentenceIsEligible(bookingId)
}
