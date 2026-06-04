UPDATE calculation_request
SET sentence_and_offences_version = 4
WHERE sentence_and_offences_version = 3
  AND calculated_at > '2026-05-10' -- released on the 11th May so this captures as few rows as possible
  AND sentence_and_offences::text like '%sdsReleaseArrangements%';
