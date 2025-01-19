alter table media add column created_at bigint;

create table project_media (
    project_id int,
    media_id int,

    constraint fk_project_media_media_id foreign key (media_id)
        references media
        on delete cascade
        on update cascade,

    constraint fk_project_media_project_id foreign key (project_id)
        references project
        on delete cascade
        on update cascade
)