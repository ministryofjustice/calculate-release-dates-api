package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

enum class ValidationOrder(
  val orderToValidate: Int,
) {
  /* The calculation is not supported by CRDS. */
  UNSUPPORTED(1),

  /* The calculation is supported but requires some data to be corrected before proceeding. */
  INVALID(2),

  /* The calculation is supported but there are warnings to acknowledge before continuing */
  WARNING(3),
  ;

  companion object {
    fun allValidations(): ValidationOrder = ValidationOrder.entries.maxBy { it.orderToValidate }
  }
}
