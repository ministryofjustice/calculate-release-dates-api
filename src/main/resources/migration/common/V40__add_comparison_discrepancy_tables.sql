create table discrepancy_impact
(
    id serial constraint discrepancy_impact_pk primary key,
    impact varchar(50) not null
);

create table discrepancy_category
(
    id serial constraint discrepancy_category_pk primary key,
    category varchar(50) not null,
    sub_category varchar(50)
);

create table discrepancy_priority
(
    id serial constraint discrepancy_priority_pk primary key,
    priority varchar(50) not null
);

create table comparison_person_discrepancy
(
    id serial constraint comparison_person_discrepancy_pk primary key,
    impact_id integer not null,
    category_id integer not null,
    other text,
    detail text,
    priority_id integer not null,
    action text,
    created_by varchar(100) not null,
    created_at timestamp with time zone not null,
    superseded_by integer,
    constraint fk_discrepancy_impact foreign key (impact_id) references discrepancy_impact(id),
    constraint fk_discrepancy_category foreign key (category_id) references discrepancy_category(id),
    constraint fk_discrepancy_priority foreign key (priority_id) references discrepancy_priority(id),
    constraint fk_discrepancy_superseded_by foreign key (superseded_by) references comparison_person_discrepancy(id)
);
