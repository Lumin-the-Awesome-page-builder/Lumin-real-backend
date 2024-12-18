clojure -M -e "(compile 'exposed-bash-service)"
bb -cp $(clojure -Spath) uberjar Bash-helper.jar -m exposed-bash-service
java -jar -Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory Bash-helper.jar