# DevPilot development instructions

Read `../docs/DevPilot_企业研发多Agent平台_实施规格书.md` and `docs/architecture.md` before changing product behavior.

Implement one phase from section 18 at a time. Do not add placeholder Agent, Tool, RAG, persistence, or external-integration implementations to make a phase look complete. Every active phase must end with its relevant tests and build commands passing.

The application is a modular monolith. Controllers call application services; Agent consumers call capability definitions through the runtime registries; providers own infrastructure access. Model-visible state must be reconstructable from the append-only session event log once Phase 1 exists.

Never commit secrets. Tool execution must not bypass validation, authorization, lifecycle events, or result limits. Keep APIs under `/api/v1` and return the common `Result` envelope for non-streaming endpoints.

