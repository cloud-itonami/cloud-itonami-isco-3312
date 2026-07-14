(ns underwriting.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [underwriting.store :as store]
            [underwriting.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Lending"})
    (store/register-application! st {:application-id "L-1" :client-id "client-1"
                                     :name "application-042"
                                     :approved-amount 20000
                                     :credit-assessment-completed? true})
    st))

(defn- disburse-op [amount]
  {:op :approve-disbursement :effect :propose :application-id "L-1"
   :disbursement-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-approved-and-completed
  (let [st (fresh-store)
        v (governor/check req {} (disburse-op 15000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-approved-boundary
  (testing "the approved-amount ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (disburse-op 20000) st)]
      (is (:ok? v)))))

(deftest hard-on-disbursement-exceeds-approved
  (testing "disbursing beyond the underwriting-approved amount is unauthorized lending, not flexible service"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (disburse-op 50000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :disbursement-exceeds-approved (:rule %)) (:violations v))))))

(deftest hard-on-credit-assessment-not-completed
  (testing "approving a loan without a completed credit assessment is an uninformed lending decision, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Lending"})
      (store/register-application! st {:application-id "L-1" :client-id "client-1"
                                       :name "application-042"
                                       :approved-amount 20000
                                       :credit-assessment-completed? false})
      (let [v (governor/check req {} (assoc (disburse-op 15000) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :credit-assessment-not-completed (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-application
  (let [st (fresh-store)
        v (governor/check req {} (assoc (disburse-op 15000) :application-id "L-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-application (:rule %)) (:violations v)))))

(deftest hard-on-foreign-application
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (disburse-op 15000) st)]
      (is (:hard? v))
      (is (some #(= :application-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (disburse-op 15000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (disburse-op 15000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-approved-disbursement-even-at-high-confidence
  (testing "no loan disbursement above the applicant's registered underwriting-approved amount without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-approved-disbursement :effect :propose
                                    :application-id "L-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-loan-approval-override-even-at-high-confidence
  (testing "overriding a declined underwriting decision always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-loan-approval-override :effect :propose
                                    :application-id "L-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (disburse-op 15000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
