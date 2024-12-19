create table configuration (
    id serial primary key,
    name text,
    path text,
    mapping text,
    created_at bigint
);