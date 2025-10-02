package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.SentenceCalcDates
import java.time.LocalDate
import java.util.stream.Stream

internal class SentenceCalcDatesTest {

  @Test
  fun `manually entered dates should not cause a mismatch`() {
    val sentenceCalcDates = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2022, 5, 5),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    )

    val differentManualDate = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2024, 5, 5),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    )

    assertThat(sentenceCalcDates.isSameComparableCalculatedDates(differentManualDate)).isTrue
  }

  @Test
  fun `a different ESED should not cause a mismatch`() {
    val sentenceCalcDates = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2022, 5, 5),
    )

    val differentEffectiveSentencedEndDate = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2024, 5, 5),
    )

    assertThat(sentenceCalcDates.isSameComparableCalculatedDates(differentEffectiveSentencedEndDate)).isTrue
  }

  @ParameterizedTest
  @MethodSource("comparableDateModifiers")
  fun `different comparable calculated dates are not the same`(dateType: ReleaseDateType, modifier: (LocalDate, SentenceCalcDates) -> SentenceCalcDates) {
    val sentenceCalcDates = modifier(LocalDate.of(2020, 1, 2), BLANK_SENTENCE_CALC_DATES)
    val differentSentenceExpiryCalculatedDate = modifier(LocalDate.of(2021, 2, 3), BLANK_SENTENCE_CALC_DATES)
    assertThat(sentenceCalcDates.isSameComparableCalculatedDates(differentSentenceExpiryCalculatedDate)).describedAs("Expected dates to be different for $dateType").isFalse
  }

  @ParameterizedTest
  @MethodSource("comparableDateModifiers")
  fun `same comparable calculated dates are the same`(dateType: ReleaseDateType, modifier: (LocalDate, SentenceCalcDates) -> SentenceCalcDates) {
    val sentenceCalcDates = modifier(LocalDate.of(2020, 1, 2), BLANK_SENTENCE_CALC_DATES)
    val differentSentenceExpiryCalculatedDate = modifier(LocalDate.of(2020, 1, 2), BLANK_SENTENCE_CALC_DATES)
    assertThat(sentenceCalcDates.isSameComparableCalculatedDates(differentSentenceExpiryCalculatedDate)).describedAs("Expected dates to be equal for $dateType").isTrue
  }

  @Test
  fun `user override dates take precedence over calculated dates`() {
    val sentenceCalcDates = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2023, 11, 21),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    )

    val overridenSentenceCalcDates = SentenceCalcDates(
      LocalDate.of(2023, 11, 22),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      LocalDate.of(2023, 11, 7),
      LocalDate.of(2023, 11, 21),
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    )

    assertThat(sentenceCalcDates.isSameComparableCalculatedDates(overridenSentenceCalcDates)).isTrue
  }

  companion object {
    @JvmStatic
    fun comparableDateModifiers(): Stream<Arguments> = Stream.of(
      Arguments.of(ReleaseDateType.SED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(sentenceExpiryCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.ARD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(automaticReleaseDate = date) }),
      Arguments.of(ReleaseDateType.CRD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(conditionalReleaseDate = date) }),
      Arguments.of(ReleaseDateType.NPD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(nonParoleDate = date) }),
      Arguments.of(ReleaseDateType.PRRD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(postRecallReleaseDate = date) }),
      Arguments.of(ReleaseDateType.LED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(licenceExpiryCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.HDCED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(homeDetentionCurfewEligibilityCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.PED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(paroleEligibilityCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.ETD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(etdCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.MTD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(mtdCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.LTD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(ltdCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.TUSED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(topupSupervisionExpiryCalculatedDate = date) }),
      Arguments.of(ReleaseDateType.DPRRD, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(dtoPostRecallReleaseDate = date) }),
      Arguments.of(ReleaseDateType.ERSED, { date: LocalDate, sentenceCalDates: SentenceCalcDates -> sentenceCalDates.copy(earlyRemovalSchemeEligibilityDate = date) }),
    )
    private val BLANK_SENTENCE_CALC_DATES = SentenceCalcDates(
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    )
  }
}
