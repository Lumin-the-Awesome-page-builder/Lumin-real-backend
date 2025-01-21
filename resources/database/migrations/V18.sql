insert into configuration (name, path, mapping, created_at)
values (
    'jvm-postgres',
    '/home/jvm-envs/jvm-postgres-env',
    '{"bash":{"path":"jvm/app.jar","title":"JAR","description":"JAR file to run"},"postgres":{"path":"pg_init/init.sql","title":"SQL","description":"SQL file with database dump"}}',
    1
)