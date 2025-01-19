create table media (
    id serial primary key,
    owner_id int,
    name text,

    constraint fk_media_owner_id foreign key (owner_id)
        references users
        on delete cascade
        on update cascade
);