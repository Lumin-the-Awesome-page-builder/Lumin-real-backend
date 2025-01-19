insert into category (name) values ('category');

insert into configuration
    (name, path, mapping, created_at)
    values ('Pepe', '/home/docker-path/test-env/simple-env', '{"bash":{"path":"home/bash.sh","title":"bash","description":"bash file to execute"}}', 1)