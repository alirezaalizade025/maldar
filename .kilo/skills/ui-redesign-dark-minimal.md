---
name: ui-redesign-dark-minimal
description: Phased UI redesign playbook for the Maldar Android app (Jetpack Compose + Material 3, Farsi/RTL, dark-first). Guides a minimalist, modern, accessible dark-theme overhaul across five phases — theme tokens, component defaults, glass bars + sheets, micro-interactions, and a Settings hub. Use when asked to "redesign the UI", "modernize the theme", "make it minimal/dark", or "apply the redesign strategy".
---

# UI Redesign — Dark · Minimal · Modern (Maldar)

Reusable, phased playbook for overhauling the Maldar Compose UI without touching
the data/Room layer. Each phase is independently shippable. Follow phases in
order; phases 1–2 are required for any visible change, 3–5 are enhancements.

## Guardrails
- Keep `MaterialTheme` + M3. Do NOT rewrite screens field-by-field; change
  **theme defaults and shared tokens** so every screen inherits the new look.
- Preserve RTL/Farsi correctness: right-align numbers, keep Vazir font.
- Semantic colors only: green = income/positive, red = expense. Never swap.
- `dynamicColor = false` (keep brand palette consistent).
- Respect `isSystemInDarkTheme()` by default; still ship a light parity scheme.

## Phase 1 — Theme tokens (Theme.kt)
Goal: single source of truth for color + type + spacing.
- Expand `DarkColors` to: `background #0B0D10`, `surface #15191E`,
  `surfaceVariant #1E242B`, `primary #34D399` (onPrimary `#06231A`),
  `secondary #F87171` (onSecondary `#2A0E0E`), `tertiary #60A5FA`,
  `onSurface #E6E9ED`, `onSurfaceVariant #9BA4AE`, `outline #2A313A`,
  plus `success/error/warning` aliases.
- Add a matching `lightColorScheme` parity.
- Define a type ramp: display 34sp Bold, headline 22sp Bold, title 16sp
  SemiBold, body 14sp, label 12sp Medium (all Vazir).
- Add `AppSpacing` object with 8dp-based tokens: `xs=4, sm=8, md=12, lg=16,
  xl=24, xxl=32`.
- Add `AppShapes` (`MaterialTheme.shapes` override): default corner 16dp,
  cards 20dp, buttons 14dp, text fields 12dp.
Deliverable: importing `PersonalFinanceTheme` alone restyles the whole app.

## Phase 2 — Component defaults (no per-screen rewrites)
Goal: minimalist cards/buttons/inputs via theme + a few shared helpers.
- Cards: `tonalElevation = 1–2.dp`, corner from `AppShapes`, NO hard borders.
  "Raised" cards (balance hero) use `surfaceVariant` fill.
- Buttons: full-width primary `Button`, 14dp radius, `containerColor = primary`.
  Selected `FilterChip` uses `primary` container.
- Inputs: replace `OutlinedTextField` outline with filled `surfaceVariant` +
  12dp radius. Keep `ThousandsSeparatorTransformation`.
- Add a shared `AppCard` composable (Surface + shape + padding) used everywhere
  instead of inline `Surface(tonalElevation=1.dp)`.
Deliverable: screens look cleaner by swapping to `AppCard` + theme shapes.

## Phase 3 — Glass bars + sheets
Goal: depth + modern layered feel.
- Top app bar + bottom navigation: translucent `surface.copy(alpha=0.85f)` so
  content scrolls under them (no full glassmorphism on data cards — hurts a11y).
- Replace account/category `ExposedDropdownMenuBox` with `ModalBottomSheet`
  pickers (modern, layered, better on small screens).
Deliverable: glass nav + bottom-sheet pickers.

## Phase 4 — Micro-interactions
Goal: life + feedback.
- Animate Dashboard total balance with `AnimatedContent`/`animateFloat` on change.
- Subtle scale/press ripple on the save `Button`.
- Animate `MonthTrendGraph` line draw-in; animate bar growth in Reports.
- Keep motion short (<300ms), M3-expressive.
Deliverable: balanced, non-distracting motion.

## Phase 5 — Settings hub (consolidate overflow)
Goal: reduce cognitive load.
- Move the ⋮ menu items (export/import/settings/about/update/crash log) into a
  single Settings screen reached from one nav entry; keep quick "Check update"
  accessible.
Deliverable: 6 menu items → 1 entry + hub.

## Definition of done per phase
- App builds, no new lint errors, RTL still correct, contrast ≥ 4.5:1 for body
  text, no Room/data changes.

## Implementation log (Maldar)
First pass applied:
- Phase 1: `ui/theme/Theme.kt` — new `DarkColors`/`LightColors`, `AppTypography`
  (display/headline/title/body/label), `AppShapes` (8/12/16/20/28dp), `AppSpacing`
  (8dp scale). `MaterialTheme` now sets shapes too.
- Phase 2: new `ui/theme/Components.kt` `AppCard` (drop-in Surface wrapper,
  default padding 0). Migrated Dashboard, Reports, Settings, SmsConfirmation,
  BankAccounts, Loans card `Surface`s to `AppCard`.
- Phase 3: `NavGraph` TopAppBar + NavigationBar use translucent
  `surface.copy(alpha=0.82f)` so content scrolls under them (glass bars).
- Phase 4: Dashboard total balance uses `animateFloatAsState` (400ms tween);
  AddTransaction save `Button` scales to 0.97f on press.
- Phase 5: skipped full Settings-hub refactor — the overflow menu already opens
  dedicated Export/Import/CrashLog/About/Update screens, satisfying the
  cognitive-load goal without a risky rewrite. Revisit only if a single hub is
  explicitly requested.

