(ns clara.test-accumulators
  (:use clojure.test
        clara.rules
        clara.rules.testfacts)
  (:require [clara.sample-ruleset :as sample]
            [clojure.set :as s]
            [clara.rules.accumulators :as acc]
            [clara.rules.dsl :as dsl])
  (import [clara.rules.testfacts Temperature WindSpeed Cold ColdAndWindy LousyWeather First Second Third Fourth]))

(deftest test-max
  (let [hottest (dsl/parse-query [] [[?t <- (acc/max :temperature) from [Temperature]]])

        session (-> (mk-session [hottest])
                    (insert (->Temperature 30 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    (is (= {:?t 80} (first (query session hottest))))))


(deftest test-min-max-average
  (let [coldest  (dsl/parse-query [] [[?t <- (acc/min :temperature) :from [Temperature]]])
        coldest-fact (dsl/parse-query [] [[?t <- (acc/min :temperature :returns-fact true) from [Temperature]]])

        hottest (dsl/parse-query [] [[?t <- (acc/max :temperature) from [Temperature]]])
        hottest-fact (dsl/parse-query [] [[?t <- (acc/max :temperature :returns-fact true) from [Temperature]]])

        average-temp (dsl/parse-query [] [[?t <- (acc/average :temperature) from [Temperature]]])

        session (-> (mk-session [coldest coldest-fact hottest hottest-fact average-temp])
                    (insert (->Temperature 30 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    (is (= {:?t 10} (first (query session coldest))))
    (is (= #{{:?t (->Temperature 10 "MCI")}}
           (set (query session coldest-fact))))

    (is (= {:?t 80} (first (query session hottest))))


    (is (= #{{:?t (->Temperature 80 "MCI")}}
           (set (query session hottest-fact))))

    (is (= (list {:?t 40}) (query session average-temp)))))

(deftest test-sum
  (let [sum (dsl/parse-query [] [[?t <- (acc/sum :temperature) from [Temperature]]])

        session (-> (mk-session [sum])
                    (insert (->Temperature 30 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    (is (= {:?t 120} (first (query session sum))))))

(deftest test-count
  (let [count (dsl/parse-query [] [[?c <- (acc/count) from [Temperature]]])

        session (-> (mk-session [count])
                    (insert (->Temperature 30 "MCI"))
                    (insert (->Temperature 10 "MCI"))
                    (insert (->Temperature 80 "MCI")))]

    (is (= {:?c 3} (first (query session count))))))

(deftest test-distinct
  (let [distinct (dsl/parse-query [] [[?t <- (acc/distinct) from [Temperature]]])
        distinct-field (dsl/parse-query [] [[?t <- (acc/distinct :temperature) from [Temperature]]])

        session (-> (mk-session [distinct distinct-field])
                    (insert (->Temperature 80 "MCI"))
                    (insert (->Temperature 80 "MCI"))
                    (insert (->Temperature 90 "MCI")))]

    (is (= #{{:?t #{ (->Temperature 80 "MCI")
                     (->Temperature 90 "MCI")}}}
           (set (query session distinct))))

    (is (= #{{:?t #{ 80 90}}}
           (set (query session distinct-field))))))

(deftest test-max-min-avg
  ;; Tests a single query that gets the maximum, minimum, and average temperatures.
  (let [max-min-avg (dsl/parse-query [] [[?max <- (acc/max :temperature) from [Temperature]]
                                  [?min <- (acc/min :temperature) from [Temperature]]
                                  [?avg <- (acc/average :temperature) from [Temperature]]])

        session (-> (mk-session [max-min-avg])
                    (insert (->Temperature 30 "MCI")
                            (->Temperature 10 "MCI")
                            (->Temperature 80 "MCI")))]

    (is (= {:?max 80 :?min 10 :?avg 40} (first (query session max-min-avg))))))


(deftest test-count-none
  (let [count (dsl/parse-query [] [[?c <- (acc/count) from [Temperature]]])

        session (mk-session [count])]

    (is (= {:?c 0} (first (query session count))))))

(deftest test-count-none-joined
  (let [count (dsl/parse-query [] [[WindSpeed (> windspeed 10) (= ?loc location)]
                            [?c <- (acc/count) from [Temperature (= ?loc location)]]])

        session (-> (mk-session [count])
                    (insert (->WindSpeed 20 "MCI")))]

    (is (= {:?c 0 :?loc "MCI"} (first (query session count))))))



;; Same as the above test, but the binding occurs in a rule after
;; the accumulator, to test reordering.
(deftest test-count-none-with-later-bind
  (let [count (dsl/parse-query [] [[?c <- (acc/count) from [Temperature (= ?loc location)]]
                            [WindSpeed (> windspeed 10) (= ?loc location)]])

        session (-> (mk-session [count])
                    (insert (->WindSpeed 20 "MCI")))]

    (is (= {:?c 0 :?loc "MCI"} (first (query session count))))))


(deftest test-count-some-empty
  (let [count (dsl/parse-query [:?loc] [[?c <- (acc/count) from [Temperature (= ?loc location)]]
                                 [WindSpeed (> windspeed 10) (= ?loc location)]])

        session (-> (mk-session [count])
                    (insert (->WindSpeed 20 "MCI")
                            (->WindSpeed 20 "SFO")
                            (->Temperature 40 "SFO")
                            (->Temperature 50 "SFO")))]

    ;; Zero count at MCI, since no temperatures were inserted.
    (is (= {:?c 0 :?loc "MCI"} (first (query session count :?loc "MCI"))))

    ;; Ensure the two temperature readings at SFO are found as expected.
    (is (= {:?c 2 :?loc "SFO"} (first (query session count :?loc "SFO"))))))
