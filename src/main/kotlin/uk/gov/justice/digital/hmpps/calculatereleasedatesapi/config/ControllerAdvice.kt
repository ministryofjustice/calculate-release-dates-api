package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.PRECONDITION_FAILED
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.AuthorizationServiceException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.UnsupportedSentenceException
import javax.persistence.EntityNotFoundException
import javax.validation.ValidationException

@RestControllerAdvice
class ControllerAdvice {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    log.info("Access denied exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.FORBIDDEN)
      .body(
        ErrorResponse(
          status = HttpStatus.FORBIDDEN.value(),
          userMessage = "Authentication problem. Check token and roles - ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(AuthorizationServiceException::class)
  fun handleAuthorizationServiceException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    log.info("Auth service exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.UNAUTHORIZED)
      .body(
        ErrorResponse(
          status = HttpStatus.UNAUTHORIZED.value(),
          userMessage = "Authentication problem. Check token and roles - ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(RestClientResponseException::class)
  fun handleRestClientException(e: RestClientResponseException): ResponseEntity<ErrorResponse> {
    log.error("RestClientResponseException: {}", e.message)
    return ResponseEntity
      .status(e.rawStatusCode)
      .body(
        ErrorResponse(
          status = e.rawStatusCode,
          userMessage = "Rest client exception ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(RestClientException::class)
  fun handleRestClientException(e: RestClientException): ResponseEntity<ErrorResponse> {
    log.error("RestClientException: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
          userMessage = "Rest client exception ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(EntityNotFoundException::class)
  fun handleEntityNotFoundException(e: EntityNotFoundException): ResponseEntity<ErrorResponse> {
    log.info("Entity not found exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND.value(),
          userMessage = "Not found: ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> {
    log.info("Validation exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = HttpStatus.BAD_REQUEST.value(),
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(PreconditionFailedException::class)
  fun handlePreconditionFailedException(e: Exception): ResponseEntity<ErrorResponse> {
    log.error("Pre condition failed exception: {}", e.message)
    return ResponseEntity
      .status(PRECONDITION_FAILED)
      .body(
        ErrorResponse(
          status = PRECONDITION_FAILED,
          userMessage = "Pre condition failed: ${e.message}",
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(UnsupportedSentenceException::class)
  fun handleUnsupportedSentenceException(e: UnsupportedSentenceException): ResponseEntity<ErrorResponse?>? {
    log.error("Unsupported sentence exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(
        ErrorResponse(
          status = HttpStatus.BAD_REQUEST,
          errorCode = 1,
          userMessage = "One or more of the sentence types in this calculation is not currently supported in this service",
          arguments = e.sentenceAndOffences.map { it.sentenceTypeDescription }.distinct(),
          developerMessage = e.message
        )
      )
  }

  @ExceptionHandler(java.lang.Exception::class)
  fun handleException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
    log.error("Unexpected exception: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = HttpStatus.INTERNAL_SERVER_ERROR,
          userMessage = "Unexpected error: ${e.message}",
          developerMessage = e.message
        )
      )
  }
}

data class ErrorResponse(
  val status: Int,
  val errorCode: Int? = null,
  val userMessage: String? = null,
  val developerMessage: String? = null,
  val moreInfo: String? = null,
  val arguments: List<String> = listOf()
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
    moreInfo: String? = null,
    arguments: List<String> = listOf()
  ) :
    this(status.value(), errorCode, userMessage, developerMessage, moreInfo, arguments)
}
