package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.core.convert.ConversionService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.Comparison
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ComparisonPerson
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Mismatch
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ComparisonPersonRepository

@Service
class BulkComparisonService(
  private val comparisonPersonRepository: ComparisonPersonRepository,
  private val prisonService: PrisonService
) {

  fun populate(comparison: Comparison) {
    var peopleAtEstablishment = getPeopleAtEstablishment(comparison)
    identifyMismatches(peopleAtEstablishment)
  }

  private fun identifyMismatches(peopleAtEstablishment: List<SentenceSummary>) {
    var mismatches: List<Mismatch?> = peopleAtEstablishment.map(this::determineIfMismatch)
  }

  private fun determineIfMismatch(sentenceSummary: SentenceSummary):Mismatch {

    val prisonApiSourceData: PrisonApiSourceData = sentenceSummary

    var mismatch = Mismatch(false, sentenceSummary)

      // returns a mismatch object
    return mismatch
  }

  /*
  @Async
  public fun identifyMismatches(comparison: Comparison) {
    // I loop over all the people in the comparison
    val peopleToCompare = comparisonPersonRepository.findByComparisonIdIs(comparison.id)

    // I analyse the results,
    // peopleToCompare.forEach { person -> this.determineIfMismatch(person, comparison) }

    // then I change the state of the analysis
    // comparison.comparisonStatus = ComparisonStatus(ComparisonStatusValue.ACTIVE)
    // comparisonRepository.save(comparison)
  }

  private fun determineIfMismatch(person: ComparisonPerson, comparison: Comparison) {
    // Not yet implemented

    // if any are a mismatch

    // record any mismatch in the database
  }
  */

  @Async
  fun getPeopleAtEstablishment(comparison: Comparison): List<SentenceSummary> {
    if (!comparison.manualInput && comparison.prison != null) {
      val activeBookingsAtEstablishment = prisonService.getActiveBookingsByEstablishment(comparison.prison)
      val comparisonPeople = activeBookingsAtEstablishment.filter { it.prisonerNumber != null || it.latestPrisonTerm != null }.map {
        ComparisonPerson(
          comparisonId = comparison.id,
          personId = it.prisonerNumber!!,
          latestBookingId = it.latestPrisonTerm!!.bookingId,
        )
      }
      // record all the people we are going to run comparison for
      comparisonPersonRepository.saveAll(comparisonPeople)
      return activeBookingsAtEstablishment

    } else {
      TODO("Not yet implemented - throw?")
    }
  }
}
