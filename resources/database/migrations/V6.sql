create table environment (
                             id serial primary key,
                             name text,
                             owner_id int,
                             path text,
                             constraint fk_environment_owner_id foreign key (owner_id)
                                 references users
                                 on delete cascade
                                 on update cascade
);
create table container (
                           id serial primary key,
                           name text,
                           status text,
                           environment_id int,
                           constraint fk_container_environment_id foreign key (environment_id)
                               references environment
                               on delete cascade
                               on update cascade
);
alter table environment add column created_at bigint;
alter table container add column created_at bigint;