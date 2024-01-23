create table comparison_person_discrepancy_impact
(
    id serial constraint discrepancy_impact_pk primary key,
    impact varchar(30) not null
);

create table comparison_person_discrepancy_priority
(
    id serial constraint discrepancy_priority_pk primary key,
    priority varchar(30) not null
);

create table comparison_person_discrepancy
(
    id serial constraint comparison_person_discrepancy_pk primary key,
    comparison_person_id integer not null,
    impact_id integer not null,
    priority_id integer not null,
    detail text,
    action text,
    created_by varchar(40) not null,
    created_at timestamp with time zone not null,
    superseded_by_id integer,
    constraint fk_comparison_person foreign key (comparison_person_id) references comparison_person(id),
    constraint fk_discrepancy_impact foreign key (impact_id) references comparison_person_discrepancy_impact(id),
    constraint fk_discrepancy_priority foreign key (priority_id) references comparison_person_discrepancy_priority(id),
    constraint fk_discrepancy_superseded_by foreign key (superseded_by_id) references comparison_person_discrepancy(id)
);

create table comparison_person_discrepancy_cause
(
    id serial constraint discrepancy_category_pk primary key,
    discrepancy_id integer not null,
    category varchar(10) not null,
    sub_category varchar(30),
    detail text,
    constraint fk_category_discrepancy foreign key (discrepancy_id) references comparison_person_discrepancy(id)
);

insert into comparison_person_discrepancy_impact(id, impact) values (0, 'POTENTIAL_RELEASE_IN_ERROR');
insert into comparison_person_discrepancy_impact(id, impact) values (1, 'POTENTIAL_UNLAWFUL_DETENTION');
insert into comparison_person_discrepancy_impact(id, impact) values (2, 'OTHER');

insert into comparison_person_discrepancy_priority(id, priority) values (0, 'LOW_RISK');
insert into comparison_person_discrepancy_priority(id, priority) values (1, 'MEDIUM_RISK');
insert into comparison_person_discrepancy_priority(id, priority) values (2, 'HIGH_RISK');
