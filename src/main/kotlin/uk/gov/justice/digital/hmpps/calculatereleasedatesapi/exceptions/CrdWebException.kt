package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

open class CrdWebException (override var message: String,
                            var status: HttpStatus,
                            var code: String? = null) : Exception(message){
}