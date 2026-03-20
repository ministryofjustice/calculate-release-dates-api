package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.client

import io.netty.handler.timeout.ReadTimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.io.IOException
import java.time.Duration

class RetryTest {

  private val exchangeFunction: ExchangeFunction = mock(ExchangeFunction::class.java)
  private val webClient: WebClient = WebClient.builder().exchangeFunction(exchangeFunction).build()

  @Test
  fun `should return without retry if successful`() {
    val response = ClientResponse.create(HttpStatus.OK)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .body("{ \"answer\": true }").build()

    whenever(exchangeFunction.exchange(any())).thenReturn(Mono.just(response))

    val body = webClient.get()
      .uri("/test")
      .retrieve()
      .bodyToMono<Body>()
      .loggingRetry(log, "test")
      .block()

    assertThat(body).isEqualTo(Body(true))
  }

  @Test
  fun `should retry 500 errors up to the max times and still get the response if successful`() {
    val errorResponse = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()

    val successResponse = ClientResponse.create(HttpStatus.OK)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .body("{ \"answer\": true }").build()

    whenever(exchangeFunction.exchange(any())).thenReturn(
      Mono.just(errorResponse),
      Mono.just(errorResponse),
      Mono.just(errorResponse),
      Mono.just(successResponse),
    )

    val body = webClient.get()
      .uri("/test")
      .retrieve()
      .bodyToMono<Body>()
      .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
      .block()

    assertThat(body).isEqualTo(Body(true))

    verify(exchangeFunction, times(4)).exchange(any())
  }

  @Test
  fun `should retry IOException up to the max times and still get the response if successful`() {
    val successResponse = ClientResponse.create(HttpStatus.OK)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .body("{ \"answer\": true }").build()

    whenever(exchangeFunction.exchange(any())).thenReturn(
      Mono.error(IOException("Test")),
      Mono.error(IOException("Test")),
      Mono.error(IOException("Test")),
      Mono.just(successResponse),
    )

    val body = webClient.get()
      .uri("/test")
      .retrieve()
      .bodyToMono<Body>()
      .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
      .block()

    assertThat(body).isEqualTo(Body(true))

    verify(exchangeFunction, times(4)).exchange(any())
  }

  @Test
  fun `should retry netty timeout up to the max times and still get the response if successful`() {
    val successResponse = ClientResponse.create(HttpStatus.OK)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .body("{ \"answer\": true }").build()

    whenever(exchangeFunction.exchange(any())).thenReturn(
      Mono.error(ReadTimeoutException()),
      Mono.error(ReadTimeoutException()),
      Mono.error(ReadTimeoutException()),
      Mono.just(successResponse),
    )

    val body = webClient.get()
      .uri("/test")
      .retrieve()
      .bodyToMono<Body>()
      .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
      .block()

    assertThat(body).isEqualTo(Body(true))

    verify(exchangeFunction, times(4)).exchange(any())
  }

  @Test
  fun `should throw exception if the max retry times are exceeded`() {
    val errorResponse = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()

    whenever(exchangeFunction.exchange(any())).thenReturn(Mono.just(errorResponse))

    try {
      webClient.get()
        .uri("/test")
        .retrieve()
        .bodyToMono<Body>()
        .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
        .block()
      fail("Expected an exception")
    } catch (e: Exception) {
      assertThat(e).hasMessageContaining("Max retries - test")
    }

    verify(exchangeFunction, times(4)).exchange(any())
  }

  @Test
  fun `should throw exception without retry if the error code is a 400`() {
    val errorResponse = ClientResponse.create(HttpStatus.BAD_REQUEST).build()

    whenever(exchangeFunction.exchange(any())).thenReturn(Mono.just(errorResponse))

    try {
      webClient.get()
        .uri("/test")
        .retrieve()
        .bodyToMono<Body>()
        .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
        .block()
      fail("Expected an exception")
    } catch (e: Exception) {
      assertThat(e).hasMessageContaining("400 Bad Request")
    }

    verify(exchangeFunction, times(1)).exchange(any())
  }

  @Test
  fun `should throw exception without retry if the error code is a 404`() {
    val errorResponse = ClientResponse.create(HttpStatus.NOT_FOUND).build()

    whenever(exchangeFunction.exchange(any())).thenReturn(Mono.just(errorResponse))

    try {
      webClient.get()
        .uri("/test")
        .retrieve()
        .bodyToMono<Body>()
        .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
        .block()
      fail("Expected an exception")
    } catch (e: Exception) {
      assertThat(e).hasMessageContaining("404 Not Found")
    }

    verify(exchangeFunction, times(1)).exchange(any())
  }

  @Test
  fun `should throw exception without retry if there is some other kind of exception`() {
    whenever(exchangeFunction.exchange(any())).thenReturn(Mono.error(RuntimeException("Big bang!")))

    try {
      webClient.get()
        .uri("/test")
        .retrieve()
        .bodyToMono<Body>()
        .loggingRetry(log, "test", maxAttempts = 3, minBackoff = Duration.ofMillis(10), maxBackoff = Duration.ofSeconds(10))
        .block()
      fail("Expected an exception")
    } catch (e: Exception) {
      assertThat(e).hasMessageContaining("Big bang!")
    }

    verify(exchangeFunction, times(1)).exchange(any())
  }

  private data class Body(val answer: Boolean)
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
