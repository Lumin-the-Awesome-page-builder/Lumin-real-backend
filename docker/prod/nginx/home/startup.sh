#!/bin/bash
/home/wait-for-it.sh back:8080 -s -t 60
nginx -g "daemon off;"
