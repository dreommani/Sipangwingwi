# Sipangwingwi Codex Instructions

Sipangwingwi is a Kotlin Multiplatform application for Kenyan small
businesses, restaurants and small supermarkets.

Before planning or implementing a task, read:

- `docs/product/PRODUCT.md`
- `docs/brand/BRAND.md`
- `docs/architecture/DECISIONS.md`

## Working rules

- Android is the first delivery priority.
- Share business logic through Kotlin Multiplatform.
- Use Compose Multiplatform for the UI.
- The system must be offline-first.
- M-Pesa credentials and callbacks belong on the backend.
- Every inventory change must create a stock-movement record.
- Do not introduce payroll, accounting or e-commerce without approval.
- Do not change approved branding without updating `docs/brand/BRAND.md`.
- Before coding, summarize the relevant requirements and affected files.
- After coding, run appropriate tests and document important decisions.
- When changing user-facing UI behavior, update `docs/product/UI_WORKFLOW.md`
  in the same change.

## Branding and design system

Before creating or modifying user-interface code, icons, themes, marketing
screens, receipts or visual assets, read `docs/brand/BRAND.md`.

The approved brand source of truth is `docs/brand/BRAND.md`.

Rules:

- Do not invent new primary brand colours.
- Do not change approved colour values without explicit approval.
- Do not replace the approved logo concept.
- Use semantic colours for success, warning, error and information states.
- Ensure new UI supports light and dark themes.
- Ensure receipt branding also works in monochrome.
- Reuse design tokens instead of placing raw colour values throughout UI code.
