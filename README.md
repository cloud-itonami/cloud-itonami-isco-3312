# cloud-itonami-isco-3312

Open Occupation Blueprint for **ISCO-08 3312**: Credit and Loans Officers.

This repository designs a forkable OSS business for an independent loan origination and underwriting practice: a document intake and archival robot manages loan files and income documentation under a governor-gated actor, so the practice keeps its own underwriting records instead of renting a closed loan-origination SaaS.

**Maturity: `:implemented`.** `src/underwriting/` implements the
`LoanUnderwritingActor` as a `langgraph.graph/state-graph`
(`underwriting.actor`) wired to an `Underwriting Advisor`
(`underwriting.advisor`) and an independent `LoanUnderwritingGovernor`
(`underwriting.governor`), following the itonami actor pattern
(ADR-2607011000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 14 tests / 29 assertions green (`clojure -M:test`).
HARD invariants (always hold, never overridable): client provenance,
no-actuation (`:effect` must be `:propose`), a registered application
basis for any disbursement proposal, the proposed disbursement amount
not exceeding the application's registered underwriting-approved
amount (disbursing beyond it is unauthorized lending, not flexible
service), and a completed credit assessment before any disbursement
can proceed (approving a loan without one is an uninformed lending
decision, not efficient service). Always-escalate ops (human sign-off
regardless of confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:approve-over-approved-disbursement` and
`:approve-loan-approval-override`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document intake and archival robot performs loan-file assembly, income-document scanning and physical archival under an actor that proposes
actions and an independent **Loan Underwriting Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
loan disbursement above the applicant's registered underwriting-approved amount) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
loan application + credit file + underwriting policy
        |
        v
Underwriting Advisor -> Loan Underwriting Governor -> underwrite/approve, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3312`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
