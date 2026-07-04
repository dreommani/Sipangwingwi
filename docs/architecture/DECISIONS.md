## ADR-001 — Offline-first local database

- Status: Accepted
- Date: 2026-07-04
- Decision: The local Room database is the primary UI data source.
- Reason: Shops must continue operating during unreliable connectivity.
- Consequence: Server synchronization uses an outbox and idempotent events.