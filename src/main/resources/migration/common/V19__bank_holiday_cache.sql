-- Create new table to cache bank holiday data.
CREATE TABLE bank_holiday_cache
(
  id                          serial                        constraint bank_holiday_cache_pk PRIMARY KEY,
  data                        JSONB                         NOT NULL,
  cached_at                   timestamp with time zone      NOT NULL
);