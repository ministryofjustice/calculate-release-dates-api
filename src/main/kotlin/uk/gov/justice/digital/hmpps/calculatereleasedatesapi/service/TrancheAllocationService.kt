package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SDSEarlyReleaseTranches
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import java.time.LocalDate

@Service
class TrancheAllocationService(
  @Autowired
  private val trancheOne:TrancheOne,

  @Autowired
  private val trancheTwo:TrancheTwo,
) {


  fun calculateTranche(calculationResult: CalculationResult, booking: Booking) : SDSEarlyReleaseTranches{

    //List of sentences allowable for SDS early release
    val sdsEarlyReleaseSentences =
      booking.sentences.filter { sentence -> sentence.identificationTrack.equals(SentenceIdentificationTrack.SDS_EARLY_RELEASE) }

    var resultTranche: SDSEarlyReleaseTranches = SDSEarlyReleaseTranches.TRANCHE_0

    if (sdsEarlyReleaseSentences.isNotEmpty()) {

      if (trancheOne.isBookingApplicableForTrancheCriteria(calculationResult, booking)){
        resultTranche = SDSEarlyReleaseTranches.TRANCHE_1;
      } else if (trancheTwo.isBookingApplicableForTrancheCriteria(calculationResult, booking)){
        resultTranche = SDSEarlyReleaseTranches.TRANCHE_2;
      }
    }

   return resultTranche;
  }
}
