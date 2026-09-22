---
name: auth-audit-foundation
depth: Standard
keywords: []
description: "MasterSmith's first Intent: authentication/authorization foundation (login, JWT issue/verify, admin-flag gated admin access) integrated with the audit-log and structured-logging foundation (internal-DB audit trail, SLF4J/logback structured logs, distributed tracing, Actuator OTEL export)"
skeleton: on
change_control: strict
---

# auth-audit-foundation scope

Composed for the first Intent of a greenfield project (MasterSmith), bundling
Intent B (auth/authz: login, JWT issue/verify, admin-flag gated admin-screen
access — requirements 5.7, 7) and Intent C (audit log + structured logging:
internal-DB audit trail, SLF4J/logback structured logs, distributed tracing,
Actuator OTEL export — requirements 7) from
`reference/master-mgmt-app-requirements.md`.

Change Control defaults to strict: this foundation is security-sensitive
(authentication/authorization) and compliance-adjacent (audit trail), and
every later Intent (D through K) builds on its design decisions, so an input
that changes after approval reopens that approval rather than being merely
recorded.

## Why these stages, why skip those

This is standard-depth, net-new discovery-and-design work for a completely
greenfield repository (no existing code, no tests, no CI): intent-capture,
scope-definition, practices-discovery, requirements-analysis, domain-design,
units-generation, the full construction design set (functional-design,
nfr-requirements, nfr-design, infrastructure-design), code-generation,
build-and-test, ci-pipeline, and the full operation set (deployment-pipeline,
environment-provisioning, deployment-execution, observability-setup,
performance-validation) all execute.

Framing/discovery stages that overlap another executing stage are folded:
market-research (no external market — this is an internal admin tool),
feasibility (JWT auth via Spring Security, SLF4J/logback, and Actuator OTEL
export are standard, well-documented patterns — the viability call folds into
domain-design), team-formation (no multi-team coordination signal),
rough-mockups/refined-mockups (the only new UI surface is a standard login
form — no divergent UX to compare or invest hi-fi design in), user-stories
(only two personas — authenticated user and admin — with no conflicting
journeys; requirements-analysis captures the acceptance criteria),
contract-design and delivery-planning (only two units — auth and
audit/logging — with a single light dependency, the audit trail hooking
into auth events, expressible inline in units-generation's output).
reverse-engineering is SKIP because the project is greenfield (no existing
code to map). incident-response and feedback-optimization are SKIP because
this Intent ships foundational plumbing, not yet a production-facing
operational surface or a live feedback loop.

## Membership

Composed (not keyword-inferable): `keywords: []`. Resolve with
`--scope auth-audit-foundation`.
