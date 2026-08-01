# Maldar design system

This document is the visual source of truth for Maldar Compose work. It records the approved language extracted from Stitch project `16116636474679387747` (`خانه - داشبورد مالی`). Existing Android behavior remains the source of truth for data, navigation, calculations, permissions, and workflows.

The Phase 1 implementation lives in `app/src/main/java/com/personalfinance/tracker/ui/design`. It is isolated and is not yet applied to production screens.

## Principles

- Use Stitch for visual hierarchy and styling, not for application behavior.
- Use Material 3 semantics and interaction behavior.
- Keep components independent of entities, repositories, and ViewModels.
- Prefer semantic tokens (`positive`, `negative`, `warning`) over literal colours.
- Preserve legibility, touch targets, focus order, and screen-reader output.
- Do not copy Stitch HTML or CSS.

## Colours

### Light

| Token | Value | Purpose |
|---|---|---|
| Primary teal | `#087866` | Primary actions and selected state |
| Stitch teal | `#0AAE91` | Charts and branded accents |
| Secondary blue | `#2F8DF4` | Neutral secondary emphasis |
| Positive | `#159447` | Income, success, positive movement |
| Negative | `#DC3F3F` | Expense, error, destructive state |
| Warning | `#E38A00` | Pending and due-soon state |
| Warning container | `#FFF4D6` | Warning banner background |
| Background | `#F3F6FA` | App background |
| Surface | `#FFFFFF` | Cards and controls |
| Surface variant | `#E8EEF7` | Grouped and secondary surfaces |
| Primary text | `#111315` | High-emphasis copy |
| Secondary text | `#656A70` | Metadata and supporting copy |
| Outline | `#D5DAE1` | Borders and dividers |

### Dark

| Token | Value | Purpose |
|---|---|---|
| Background | `#0D0F12` | App background |
| Surface | `#191B20` | Cards and controls |
| Surface variant | `#202228` | Elevated/grouped surfaces |
| Primary text | `#F4F4F5` | High-emphasis copy |
| Secondary text | `#9A9DA4` | Metadata and supporting copy |
| Positive | `#35C47B` | Income and success |
| Negative | `#F05B58` | Expense and errors |
| Warning | `#F4C430` | Pending state |
| Outline | `#2A2D33` | Borders and dividers |

Material colour roles are defined separately from Maldar semantic roles. A positive value must use `MaldarDesign.colors.positive`, not assume that Material `primary` will always mean income.

## Typography

Vazir is the approved implementation font until Stitch supplies a named font asset. It is already bundled, supports Persian, and requires no dependency change.

| Role | Size / line height | Weight |
|---|---|---|
| Large screen heading | 24 / 32sp | Bold |
| Screen heading | 22 / 30sp | Bold |
| Compact heading | 20 / 28sp | Bold |
| Section title | 18 / 26sp | Bold |
| Card title | 16 / 24sp | Semibold |
| Compact title | 14 / 20sp | Medium |
| Body large | 16 / 24sp | Normal |
| Body | 14 / 22sp | Normal |
| Supporting body | 12 / 18sp | Normal |
| Button/strong label | 14 / 20sp | Semibold |
| Label | 12 / 18sp | Medium |
| Compact navigation label | 10 / 16sp | Medium |

Financial amounts normally use bold weight. Hierarchy should come from weight, colour, and spacing before introducing larger sizes.

## Spacing

The spacing system is based on a 4dp/8dp rhythm:

| Token | Value |
|---|---:|
| `xxs` | 2dp |
| `xs` | 4dp |
| `sm` | 8dp |
| `md` | 12dp |
| `lg` | 16dp |
| `xl` | 20dp |
| `xxl` | 24dp |
| `section` | 32dp |

Use 16–20dp horizontal screen padding, 12–16dp card padding, 8–12dp between related items, and 24–32dp between major sections.

## Corner radius

| Role | Radius |
|---|---:|
| Chips and small elements | 8dp |
| Inputs and controls | 12dp |
| Standard cards | 16dp |
| Hero cards | 20dp |
| Pill actions | 28dp |

Use circles for icon containers and floating add actions. Do not apply the largest radius to every surface.

## Elevation

| Token | Value | Purpose |
|---|---:|---|
| Flat | 0dp | Outlined and tonal surfaces |
| Card | 1dp | Standard cards |
| Raised | 3dp | Prominent cards |
| Floating | 6dp | Floating/persistent actions |

Dark mode should rely primarily on tonal surface contrast rather than strong shadows.

## Icon style

- Use Material filled or rounded icons already available to the project.
- Treat icons as decorative when adjacent text communicates the same meaning.
- Give standalone icon actions a localized content description and at least a 48dp target.
- Use tinted circular or rounded-square containers for transaction/category icons.
- Use semantic colour consistently: positive green, negative red, warning amber, neutral teal/blue.
- Use auto-mirrored navigation icons and logical start/end placement.

## Reusable components

| Component | Purpose |
|---|---|
| `AppCard` | Flat, outlined, raised, and hero surfaces |
| `AppButton` | Primary and outlined pill actions with accessible minimum height |
| `AmountText` | Signed, semantic financial display; accepts already-formatted text |
| `MetricCard` | Label/value/supporting-text summary card |
| `SectionHeader` | Semantic heading with optional action |
| `TransactionRow` | Model-free transaction summary with semantic icon and amount |
| `WarningBanner` | Polite screen-reader announcement for actionable warnings |
| `EmptyState` | Empty content message with optional recovery action |
| `LoadingState` | Indeterminate loading feedback with announced description |

Components accept strings and callbacks rather than database entities. Formatting and business decisions remain with their caller. Every reusable component has light and dark Persian Compose previews.

## RTL considerations

- Use `start`/`end`, not physical left/right assumptions.
- Use auto-mirrored arrows for back/forward navigation.
- Keep Compose layout direction inherited; override to LTR only for controls that require it, such as a clock face.
- Test mixed Persian text, Latin bank identifiers, signed amounts, and dates.
- Decorative icons have no content description; interactive icons require one.
- Do not infer Android navigation direction from the iOS chrome visible in Stitch exports.
- Validate charts and progress direction separately; RTL does not automatically define chronological direction.

## Currency formatting

- The Android behavioral source of truth currently stores monetary values as Toman-based `Double` values.
- The design system does not parse, round, convert, or calculate money.
- `AmountText` accepts an already-formatted amount so callers continue using the existing `Money` and `Digits` utilities.
- Default display is Persian digits, deterministic thousands grouping, then `تومان`.
- Income may use `+`; expense may use `−`; neutral balances have no sign.
- Rial mode remains an existing transaction-flow concern and must not be implemented inside visual components.
- Do not use Stitch sample values to validate application calculations.
- A future migration away from `Double` requires a separate database and backup-compatibility plan.

## Design decisions

- Teal is the brand primary; blue remains a secondary neutral accent because Stitch uses both.
- Financial meaning uses independent semantic colours, avoiding accidental coupling to Material colour roles.
- The light background uses the neutral blue-gray shared by most Stitch screens rather than the presentation-only phone frame or pure mint dashboard backdrop.
- Dark colours follow the approved dark dashboard while maintaining Material 3 contrast roles.
- Vazir is retained until a licensed, named replacement is approved.
- Component APIs are model-free to keep Phase 1 isolated from production behavior.
- Dynamic colour is intentionally excluded because it would replace the approved Stitch palette.
- The new theme is not installed in `MainActivity`; production visuals remain unchanged until a screen-specific Phase 2 is approved.

## Adoption rule

Adopt this system one approved screen at a time. Each adoption must preserve the existing destination, ViewModel calls, calculations, persistence, SMS/notification workflows, empty/loading/error states, and backup compatibility unless a separate behavioral change is explicitly approved.
