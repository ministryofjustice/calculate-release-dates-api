package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client

import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.Logger
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.MaxRetryAchievedException
import java.io.IOException
import java.time.Duration

fun <T : Any> Mono<T>.loggingRetry(logger: Logger, description: String, maxAttempts: Long = 5, minBackoff: Duration = Duration.ofMillis(100), maxBackoff: Duration = Duration.ofMillis(100)): Mono<T> = this.retryWhen(
  Retry.backoff(maxAttempts, minBackoff)
    .maxBackoff(maxBackoff)
    .filter { exception -> exception != null && (isA500Error(exception) || isAGeneralIOException(exception) || isANettyTimeout(exception)) }
    .doBeforeRetry { retrySignal ->
      logger.warn("Retrying $description [Attempt: ${retrySignal.totalRetries() + 1}] due to ${retrySignal.failure().message}. ")
    }
    .onRetryExhaustedThrow { _, _ ->
      throw MaxRetryAchievedException("Max retries - $description")
    },
)

private fun isAGeneralIOException(exception: Throwable): Boolean = exception is IOException || exception.cause is IOException

private fun isA500Error(exception: Throwable): Boolean = exception is WebClientResponseException && exception.statusCode.value() >= 500

private fun isANettyTimeout(exception: Throwable): Boolean = exception is ReadTimeoutException || exception.cause is ReadTimeoutException
