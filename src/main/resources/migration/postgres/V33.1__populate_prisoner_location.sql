update calculation_request
set prisoner_location = prisoner_details ->> 'agencyId'
where prisoner_location is null
  and prisoner_details ->> 'agencyId' is not null;
