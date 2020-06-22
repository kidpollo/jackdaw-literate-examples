(ns clojure.test)
(run-tests 'simple-app.core-test)

(ns simple-app.core)
(hello "World")

(str "foo")(+ 1)(prn "baz")

["foo" "bar" "baz"]

[["foo" "bar" "baz" "qux"]
 ["1" "2" "3" "4"]
 [1 2 3 4]
 '(:one :two :three :four)
 (take 4 (range))]

(clojure.pprint/print-table [{:a 1 :b 2 :c 3} {:b 5 :a 7 :c "dog"}])
