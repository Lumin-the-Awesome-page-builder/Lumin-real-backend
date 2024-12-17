#!/bin/bash

if [ -z "$1" ]; then
    echo "Ошибка: нужно указать путь до директории, где запущен docker-compose."
    exit 1
fi

directory=$1

cd "$directory" || { echo "Ошибка: не удалось перейти в директорию $directory"; exit 1; }

docker-compose up -d

docker-compose ps -q | while read container; do
    status=$(docker inspect --format '{{.State.Status}}' $container)
    if [ "$status" != "running" ]; then
        echo "Ошибка: контейнер $container не запустился, его статус: $status"
        exit 1
    fi
done

echo "Все контейнеры успешно запустились!"
exit 0