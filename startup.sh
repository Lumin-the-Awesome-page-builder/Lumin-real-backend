#!/bin/bash
/home/wait-for-it.sh postgres:5432 -s -t 240
bash < <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install)
curl -L -O https://github.com/clojure/brew-install/releases/latest/download/posix-install.sh
chmod +x posix-install.sh
./posix-install.sh

sleep 36000
#java -jar -Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory /home/Lumin-backend.jar