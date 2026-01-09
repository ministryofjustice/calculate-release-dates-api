package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class RestResponsePage<T : Any> : PageImpl<T> {
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  constructor(
    @JsonProperty("content") content: List<T>,
    @JsonProperty("number") number: Int,
    @JsonProperty("size") size: Int,
    @JsonProperty("totalElements") totalElements: Long,
  ) : super(content, PageRequest.of(number, size), totalElements)
}
