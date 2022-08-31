package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import java.time.temporal.ChronoUnit

class ConsecutiveSentenceAggregatorTest {

    @Test
    fun `Aggregate some durations`() {
        val durations = listOf(
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 5
            )),
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
        )

        val result = ConsecutiveSentenceAggregator(durations).aggregate()

        Assertions.assertThat(result).containsAll(listOf(
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 9
            )),
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 7
            )),
        ))
    }


    @Test
    fun `Aggregate some durations mixed units`() {
        val durations = listOf(
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 5,
                ChronoUnit.WEEKS to 2
            )),
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 3,
                ChronoUnit.WEEKS to 2
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
        )

        val result = ConsecutiveSentenceAggregator(durations).aggregate()

        Assertions.assertThat(result).containsAll(listOf(
            Duration(mapOf(
                ChronoUnit.WEEKS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 9
            )),
            Duration(mapOf(
                ChronoUnit.WEEKS to 5
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 3
            )),
            Duration(mapOf(
                ChronoUnit.WEEKS to 2
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
        ))
    }

    @Test
    fun `Aggregate some durations all the same`() {
        val durations = listOf(
            Duration(mapOf(
                ChronoUnit.MONTHS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 5
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 3
            )),
            Duration(mapOf(
                ChronoUnit.MONTHS to 4
            )),
        )

        val result = ConsecutiveSentenceAggregator(durations).aggregate()

        Assertions.assertThat(result).containsAll(listOf(
            Duration(mapOf(
                ChronoUnit.MONTHS to 22
            )),
        ))
    }
}