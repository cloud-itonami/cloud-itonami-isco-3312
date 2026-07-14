(ns underwriting.store
  "SSoT for the ISCO-08 3312 independent loan origination &
  underwriting practice actor (itonami actor pattern, ADR-2607011000
  / CLAUDE.md Actors section; README's 'Robotics premise' — a
  document intake and archival robot performs loan-file assembly,
  income-document scanning and physical archival under this
  advisor/governor pair, which never dispatches hardware itself and
  never disburses a loan above the applicant's registered
  underwriting-approved amount). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client      — a registered individual/small-business applicant
                  (:client-id, :name)
    application — a registered loan application {:application-id
                  :client-id :name :approved-amount number
                  :credit-assessment-completed? boolean}.
                  `:approved-amount` is the registered underwriting-
                  approved ceiling a proposed disbursement must not
                  exceed — disbursing beyond the underwriting-approved
                  amount is unauthorized lending, not flexible
                  service. `:credit-assessment-completed?` records
                  whether a credit assessment has been completed —
                  approving a loan without a completed credit
                  assessment is an uninformed lending decision, not
                  efficient service.
    record      — a committed operating record (a disbursed loan) —
                  written ONLY via commit-record!.
    ledger      — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (application [s application-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-application! [s a])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (application [_ application-id] (get-in @a [:applications application-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-application! [s app]
    (swap! a assoc-in [:applications (:application-id app)] app) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :applications {} :records [] :ledger []}
                                   seed)))))
