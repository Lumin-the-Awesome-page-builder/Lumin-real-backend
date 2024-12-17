clojure -M -e "(compile 'deploy)"
bb -cp $(clojure -Spath) uberjar Lumin-backend.jar -m deploy
java -jar -Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory Lumin-backend.jar