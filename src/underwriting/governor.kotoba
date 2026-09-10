(ns underwriting.governor
  "LoanUnderwritingGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every disbursement an advisor may propose for a loan application.
  The governor never dispatches hardware itself and never disburses a
  loan above the applicant's registered underwriting-approved amount.
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor. Task
  twist: a proposed disbursement amount is an arithmetic ceiling
  against the application's registered underwriting-approved amount,
  and a disbursement cannot proceed until the application's credit
  assessment has been completed.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance      — the individual/small-business
                                applicant must be registered.
    2. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never disburses a loan above the
                                registered approved amount; it only
                                gates what the advisor may
                                disburse).
    3. application basis      — a disbursement proposal must cite a
                                REGISTERED application belonging to
                                this client.
    4. approved-amount ceiling — the proposed disbursement amount must
                                not exceed the application's
                                registered `:approved-amount`
                                (disbursing beyond the underwriting-
                                approved amount is unauthorized
                                lending, not flexible service).
    5. credit-assessment completed — the application must have
                                `:credit-assessment-completed?` true
                                before any disbursement can proceed
                                (approving a loan without a completed
                                credit assessment is an uninformed
                                lending decision, not efficient
                                service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-approved-disbursement (no loan disbursement
                                above the applicant's registered
                                underwriting-approved amount without
                                the governor gate).
    7. :op :approve-loan-approval-override (overriding a declined
                                underwriting decision always requires
                                human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [underwriting.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-approved-disbursement
                                     :approve-loan-approval-override})

(defn- hard-violations [{:keys [request proposal]} client-record a]
  (let [{:keys [op disbursement-amount]} proposal
        disburse? (= :approve-disbursement op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は承認額超過の融資実行を直接実行しない）"})

      (and disburse? (nil? a))
      (conj {:rule :unknown-application :detail "未登録 application への融資実行提案は不可"})

      (and disburse? a (not= (:client-id a) (:client-id request)))
      (conj {:rule :application-wrong-client :detail "application が別 client のもの"})

      (and disburse? a (number? disbursement-amount) (> disbursement-amount (:approved-amount a)))
      (conj {:rule :disbursement-exceeds-approved
             :detail (str "実行額 " disbursement-amount " > 登録済み承認額 "
                          (:approved-amount a) "（承認額を超える実行は無許可融資であって柔軟な対応ではない）")})

      (and disburse? a (not (:credit-assessment-completed? a)))
      (conj {:rule :credit-assessment-not-completed
             :detail "信用調査未完了の application への融資実行は情報不足の与信判断であって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `underwriting.store/Store`. Pure — never
  mutates the store, never disburses a loan above the registered
  approved amount."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        a (some->> (:application-id proposal) (store/application store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record a)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
