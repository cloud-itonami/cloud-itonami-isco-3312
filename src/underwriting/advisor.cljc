(ns underwriting.advisor
  "Underwriting Advisor — the advisor named in this repository's
  README, proposing a loan-underwriting operation (disburse a loan,
  approve an over-approved disbursement, approve a loan-approval
  override) from a loan application, credit file and underwriting
  policy. Swappable mock/llm; the advisor ONLY proposes —
  `underwriting.governor` checks the approved-amount ceiling and
  credit-assessment completion independently and always escalates
  over-approved-disbursement and loan-approval-override decisions.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-disbursement|:approve-over-approved-disbursement|:approve-loan-approval-override
               :effect :propose :application-id str
               :disbursement-amount number :stake kw :confidence n
               :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake application-id disbursement-amount] :as request}]
  {:op op
   :effect :propose
   :application-id application-id
   :disbursement-amount disbursement-amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a loan-underwriting advisor. Given a request, propose an
   :op, the :application-id and :disbursement-amount, an honest
   :confidence and a :stake. Never propose a disbursement amount
   beyond the application's registered underwriting-approved amount,
   or a disbursement for an application whose credit assessment is
   incomplete — the governor checks both against the registered
   application record. Over-approved disbursements and loan-approval
   overrides always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
