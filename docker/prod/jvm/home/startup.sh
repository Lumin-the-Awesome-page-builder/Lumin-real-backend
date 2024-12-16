#!/bin/bash
/home/wait-for-it.sh postgres:5432 -s -t 240
java -jar -Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory /home/Lumin-backend.jar
