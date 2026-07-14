(ns underwriting.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [underwriting.actor :as actor]
            [underwriting.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Lending"})
    (store/register-application! st {:application-id "L-1" :client-id "client-1"
                                     :name "application-042"
                                     :approved-amount 20000
                                     :credit-assessment-completed? true})
    st))

(deftest commits-a-within-approved-completed-disbursement
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-disbursement :stake :low
                 :application-id "L-1" :disbursement-amount 15000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-approved-disbursement
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-disbursement :stake :low
                 :application-id "L-1" :disbursement-amount 50000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-approved-disbursement-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-approved-disbursement :stake :low
                 :application-id "L-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
