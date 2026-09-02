package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RasSentenceReferenceFormatterTest {

  private val courtName = "Birmingham Crown Court"
  private val sentencingDate: LocalDate = LocalDate.of(2026, 3, 12)
  private val offenceDate: LocalDate = LocalDate.of(2026, 1, 5)

  @Test
  fun `AC1 - count number only`() {
    val result = RasSentenceReferenceFormatter.format(reference(count = 2))
    assertThat(result).isEqualTo("Count 2 on case Birmingham Crown Court on 12 March 2026")
  }

  @Test
  fun `AC2 - count and offence date (offence date ignored, no case reference falls back to court name)`() {
    val result = RasSentenceReferenceFormatter.format(reference(count = 2, offenceDate = offenceDate))
    assertThat(result).isEqualTo("Count 2 on case Birmingham Crown Court on 12 March 2026")
  }

  @Test
  fun `AC3 - count and case reference`() {
    val result = RasSentenceReferenceFormatter.format(reference(count = 2, caseReference = "C894623"))
    assertThat(result).isEqualTo("Count 2 on case C894623 at Birmingham Crown Court on 12 March 2026")
  }

  @Test
  fun `AC4 - offence date and case reference`() {
    val result = RasSentenceReferenceFormatter.format(
      reference(offenceDate = offenceDate, caseReference = "C894623"),
    )
    assertThat(result).isEqualTo(
      "Offence (TH68007A Theft) committed on 5 January 2026 on case C894623 at Birmingham Crown Court on 12 March 2026",
    )
  }

  @Test
  fun `AC5 - offence date only`() {
    val result = RasSentenceReferenceFormatter.format(reference(offenceDate = offenceDate))
    assertThat(result).isEqualTo(
      "Offence (TH68007A Theft) committed on 5 January 2026 at Birmingham Crown Court on 12 March 2026",
    )
  }

  @Test
  fun `AC6 - case reference only`() {
    val result = RasSentenceReferenceFormatter.format(reference(caseReference = "C894623"))
    assertThat(result).isEqualTo("Offence (TH68007A Theft) on case C894623 at Birmingham Crown Court on 12 March 2026")
  }

  @Test
  fun `AC7 - count, case reference and offence date (offence date still ignored)`() {
    val result = RasSentenceReferenceFormatter.format(
      reference(count = 2, offenceDate = offenceDate, caseReference = "C894623"),
    )
    assertThat(result).isEqualTo("Count 2 on case C894623 at Birmingham Crown Court on 12 March 2026")
  }

  @Test
  fun `none of the optional fields present`() {
    val result = RasSentenceReferenceFormatter.format(reference())
    assertThat(result).isEqualTo("Offence (TH68007A Theft) at Birmingham Crown Court on 12 March 2026")
  }

  private fun reference(
    count: Int? = null,
    offenceDate: LocalDate? = null,
    caseReference: String? = null,
  ) = RasSentenceReference(
    count = count,
    offenceCode = "TH68007A",
    offenceDescription = "Theft",
    offenceDate = offenceDate,
    caseReference = caseReference,
    courtName = courtName,
    sentencingDate = sentencingDate,
  )
}
