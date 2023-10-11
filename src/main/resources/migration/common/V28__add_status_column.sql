create table comparison_status
(
    id   integer PRIMARY KEY,
    name varchar(20)
);
-- comment on table comparison_status is 'Lookup table to determine the status of a particular lookup';
-- comment on column comparison_status.name is 'The name of the status (loading, active, deleted)';

alter table comparison
    add comparison_status_id integer;

-- comment on column comparison.comparison_status_id is 'This is the comparison status ';

alter table comparison
    add constraint comparison_comparison_status__fk
        foreign key (comparison_status_id) references comparison_status;

-- comment on constraint comparison_comparison_status__fk on comparison is 'Constrain the value to the ones in the comparison_status table';

-- Statuses must match the values in enumerations/ComparisonStatus by ordinal
insert into comparison_status (id, name) values (0, 'Processing');
insert into comparison_status (id, name) values (1, 'Active');
insert into comparison_status (id, name) values (2, 'Complete');
insert into comparison_status (id, name) values (3, 'Archived');
