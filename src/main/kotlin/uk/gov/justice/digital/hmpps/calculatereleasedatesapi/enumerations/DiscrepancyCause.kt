package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations

data class DiscrepancyCause(
  val category: DiscrepancyCategory,
  val subCategory: DiscrepancySubCategory,
  val otherCause: String,
)

// CRD(listOf(DiscrepancySubCategory.SDS_PLUS, DiscrepancySubCategory.OTHER)),
// ERSED(listOf(DiscrepancySubCategory.NEW_RULES_NOT_APPLIED, DiscrepancySubCategory.OTHER)),
// HDC(listOf(DiscrepancySubCategory.NEW_RULES_NOT_APPLIED, DiscrepancySubCategory.FOURTEEN_DAY_RULE_NOT_APPLIED, DiscrepancySubCategory.INELIGIBLE_FOR_HDCED, DiscrepancySubCategory.RECALL_RELATED, DiscrepancySubCategory.OTHER)),
// LED(listOf(DiscrepancySubCategory.DATE_NOT_CALCULATED, DiscrepancySubCategory.OTHER)),
// PED(listOf(DiscrepancySubCategory.DATE_NOT_CALCULATED, DiscrepancySubCategory.OTHER)),
// PRRD(listOf(DiscrepancySubCategory.REMAND_OR_UAL_RELATED, DiscrepancySubCategory.OTHER)),
// SED(listOf(DiscrepancySubCategory.DATE_NOT_CALCULATED, DiscrepancySubCategory.OTHER)),
// TUSED(listOf(DiscrepancySubCategory.DATE_NOT_CALCULATED, DiscrepancySubCategory.RECALL_RELATED, DiscrepancySubCategory.OTHER)),
// OTHER
