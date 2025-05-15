package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

data class SentenceAdjustments(
  val taggedBail: Long = 0,
  val remand: Long = 0,
  val recallTaggedBail: Long = 0,
  val recallRemand: Long = 0,
  val awardedDuringCustody: Long = 0,
  val ualDuringCustody: Long = 0,
  val awardedAfterDeterminateRelease: Long = 0,
  val ualAfterDeterminateRelease: Long = 0,
  // UAL after FTR will also be included in the above UAL after determinate release.
  val ualAfterFtr: Long = 0,
  val servedAdaDays: Long = 0,
  val unusedAdaDays: Long = 0,
  val unusedLicenceAdaDays: Long = 0,
) {

  val deductions: Long
    get() = taggedBail + remand + recallTaggedBail + recallRemand

  fun adjustmentsForInitialReleaseWithoutAwarded(): Long = ualDuringCustody - deductions

  fun adjustmentsForInitialRelease(): Long = adjustmentsForInitialReleaseWithoutAwarded() + awardedDuringCustody - servedAdaDays

  private fun deductionsAndUalForWholeSentencePeriod(): Long = ualDuringCustody + ualAfterDeterminateRelease - deductions

  fun adjustmentsForLicenseExpiry(): Long = deductionsAndUalForWholeSentencePeriod()

  fun adjustmentsForTused(): Long = deductionsAndUalForWholeSentencePeriod()

  fun adjustmentsForStandardRecall(): Long = deductionsAndUalForWholeSentencePeriod()

  fun adjustmentsForFixedTermRecall(): Long = ualAfterFtr + awardedAfterDeterminateRelease

  fun totalAdjustmentDays(): Long = taggedBail +
    remand +
    recallTaggedBail +
    recallRemand +
    awardedDuringCustody +
    ualDuringCustody +
    awardedAfterDeterminateRelease +
    ualAfterDeterminateRelease +
    ualAfterFtr +
    servedAdaDays +
    unusedAdaDays +
    unusedLicenceAdaDays
}
