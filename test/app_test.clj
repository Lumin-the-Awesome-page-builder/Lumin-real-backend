(ns app-test
  (:require [clojure.test :refer [deftest is]]
            [app :refer [hello]]))

(deftest app-test
  (let [out (with-out-str (hello))]
    (is (= "Lumin backend\n" out))))
