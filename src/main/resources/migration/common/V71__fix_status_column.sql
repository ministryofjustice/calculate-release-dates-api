DELETE FROM comparison_status where id in (0,1,2,3);

insert into comparison_status (id, name) values (0, 'SETUP');
insert into comparison_status (id, name) values (1, 'PROCESSING');
insert into comparison_status (id, name) values (2, 'COMPLETED');
insert into comparison_status (id, name) values (3, 'ERROR');
