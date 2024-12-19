create table form (
    id serial primary key,
    owner_id int,
    project_id int,
    fields text,
    url_post text default null,
    usl_get text default null,
    created_at bigint,
    constraint fk_form_owner_id foreign key (owner_id)
        references users
        on delete cascade
        on update cascade,
    constraint fk_form_project_id foreign key (project_id)
        references project
        on delete cascade
        on update cascade
);

create table forms_data (
    id serial primary key,
    form_id int,
    data text,
    created_at bigint,
    constraint fk_forms_data_form_id foreign key (form_id)
        references form
        on delete cascade
        on update cascade
);