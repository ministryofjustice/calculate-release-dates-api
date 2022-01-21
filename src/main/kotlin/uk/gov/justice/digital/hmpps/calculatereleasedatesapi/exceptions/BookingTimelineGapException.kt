package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions

import org.springframework.http.HttpStatus

/*
This exception occurs when there is a gap in the booking timeline. This can happen in two ways

1. Sentence 1 CRD is 01/01/2021. Then sentence 2 is sentenced at 02/02/2021. There is a gap between
The CRD and the next sentence, which cannot be filled by adjustments or license recall.

2. Sentence 1 SLED is 01/01/2021. Then sentence 2 is sentenced at 02/02/2021. There is a gap between
The SLED and the next sentence, this should be a separate booking and therefore the data has been set up incorrectly.

 */
class BookingTimelineGapException(message: String) : CrdWebException(message, HttpStatus.UNPROCESSABLE_ENTITY)
