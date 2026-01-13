--Allow longer prisoner IOS for naming tests cases.
ALTER TABLE calculation_request ALTER COLUMN prisoner_id TYPE varchar(255);
