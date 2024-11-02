create table users (
    id serial primary key,
    login text,
    hash text,
    lastLogin bigint,
    serviceName text default 'common',
    internalServiceId bigint default 0,
    createdAt timestamptz,
    updatedAt timestamptz
);

create table category (
    id serial primary key,
    name text
);

create table widget (
    id serial primary key,
    name text,
    data text,
    public bool default false,
    stars int default 0,
    owner_id int,
    category_id int default null,
    constraint fk_widget_owner_id foreign key (owner_id)
        references users
        on delete cascade
        on update cascade,
    constraint fk_widget_category_id foreign key (category_id)
        references category
        on delete set null
        on update set null
);

create table project (
    id serial primary key,
    name text,
    data text,
    public bool default false,
    stars int default 0,
    owner_id int,
    category_id int default null,
    constraint fk_project_owner_id foreign key (owner_id)
        references users
        on delete cascade
        on update cascade,
    constraint fk_project_category_id foreign key (category_id)
        references category
        on delete set null
        on update set null
);

create table widget_tags (
    widget_id int,
    tag text,
    constraint fk_widget_tag foreign key (widget_id)
        references widget
        on delete cascade
        on update cascade
);

create table project_tags (
    project_id int,
    tag text,
    constraint fk_project_tag foreign key (project_id)
        references project
        on delete cascade
        on update cascade
);

create table published_project (
    project_id int,
    publish_domain text,
    constraint fk_project_published foreign key (project_id)
        references project
        on delete cascade
        on update cascade
)