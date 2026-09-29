---
version: alpha
name: Oinky — Piggy Scrapbook
description: A cosy diary-with-a-wallet. Cream paper, piggy pink, mint money and coin-yellow highlights; chunky rounded shapes and hand-placed stickers.
colors:
  primary: "#C2356A"
  on-primary: "#FFFFFF"
  primary-container: "#FFD9E3"
  on-primary-container: "#5C1030"
  secondary: "#2B7A57"
  on-secondary: "#FFFFFF"
  secondary-container: "#CDEFDD"
  on-secondary-container: "#0F3D2A"
  tertiary: "#855A00"
  on-tertiary: "#FFFFFF"
  tertiary-container: "#FFDC7A"
  on-tertiary-container: "#3D2800"
  error: "#BA1A1A"
  on-error: "#FFFFFF"
  error-container: "#FFDAD6"
  on-error-container: "#410002"
  neutral: "#FFF8F1"
  background: "#FFF8F1"
  surface: "#FFF8F1"
  on-surface: "#3A2A2E"
  on-surface-variant: "#6E565C"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#FFF1E8"
  surface-container: "#FBEAE0"
  surface-container-high: "#F6E4DA"
  surface-container-highest: "#F0DAD0"
  outline: "#9C8189"
  outline-variant: "#E8D2D6"
  income: "#2B7A57"
  expense: "#3A2A2E"
  dark-background: "#211A1C"
  dark-surface: "#211A1C"
  dark-on-surface: "#F2DEE2"
  dark-on-surface-variant: "#D8C0C6"
  dark-surface-container-lowest: "#1B1416"
  dark-surface-container-low: "#2B2225"
  dark-surface-container: "#302629"
  dark-surface-container-high: "#3A2F32"
  dark-surface-container-highest: "#453A3D"
  dark-primary: "#FFB1C8"
  dark-on-primary: "#5E1133"
  dark-primary-container: "#7E2448"
  dark-on-primary-container: "#FFD9E3"
  dark-secondary: "#8FD6B0"
  dark-on-secondary: "#003824"
  dark-secondary-container: "#145238"
  dark-on-secondary-container: "#CDEFDD"
  dark-tertiary: "#F2C15B"
  dark-on-tertiary: "#402D00"
  dark-tertiary-container: "#5C4000"
  dark-on-tertiary-container: "#FFDF9A"
  dark-outline: "#A38A91"
  dark-outline-variant: "#524347"
typography:
  display:
    fontFamily: Fredoka
    fontSize: 36px
    fontWeight: 600
    lineHeight: 44px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Fredoka
    fontSize: 28px
    fontWeight: 600
    lineHeight: 36px
  headline-md:
    fontFamily: Fredoka
    fontSize: 22px
    fontWeight: 600
    lineHeight: 28px
  title-lg:
    fontFamily: Fredoka
    fontSize: 18px
    fontWeight: 500
    lineHeight: 24px
  title-md:
    fontFamily: Nunito
    fontSize: 16px
    fontWeight: 700
    lineHeight: 22px
  body-lg:
    fontFamily: Nunito
    fontSize: 17px
    fontWeight: 400
    lineHeight: 26px
  body-md:
    fontFamily: Nunito
    fontSize: 15px
    fontWeight: 400
    lineHeight: 22px
  body-sm:
    fontFamily: Nunito
    fontSize: 13px
    fontWeight: 400
    lineHeight: 18px
  label-lg:
    fontFamily: Nunito
    fontSize: 14px
    fontWeight: 700
    lineHeight: 20px
  label-md:
    fontFamily: Nunito
    fontSize: 12px
    fontWeight: 700
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Nunito
    fontSize: 11px
    fontWeight: 600
    lineHeight: 14px
    letterSpacing: 0.02em
  amount-lg:
    fontFamily: Nunito
    fontSize: 24px
    fontWeight: 800
    lineHeight: 30px
    fontFeature: '"tnum"'
  amount-md:
    fontFamily: Nunito
    fontSize: 15px
    fontWeight: 700
    lineHeight: 20px
    fontFeature: '"tnum"'
rounded:
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  full: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  card-padding: 16px
  touch-target: 48px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.full}"
    height: 48px
    padding: 24px
  button-tonal:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.full}"
    height: 48px
    padding: 24px
  button-text:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.label-lg}"
    rounded: "{rounded.full}"
  card:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: "{spacing.card-padding}"
  card-hero:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.amount-lg}"
    rounded: "{rounded.xl}"
    padding: "{spacing.lg}"
  chip:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
    height: 32px
  chip-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
  input-field:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: "{spacing.md}"
  diary-note:
    backgroundColor: "{colors.surface-container-lowest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-lg}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md}"
  quick-entry-bar:
    backgroundColor: "{colors.surface-container-high}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body-md}"
    rounded: "{rounded.full}"
    height: 52px
  calendar-day:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label-md}"
    rounded: "{rounded.md}"
  calendar-day-today:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
  mood-face:
    size: 40px
  mood-face-selected:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.lg}"
  sticker:
    size: 88px
  amount-expense:
    textColor: "{colors.expense}"
    typography: "{typography.amount-md}"
  amount-income:
    textColor: "{colors.income}"
    typography: "{typography.amount-md}"
  badge-trip:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    typography: "{typography.label-md}"
    rounded: "{rounded.full}"
  banner-reminder:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: "{spacing.md}"
  navigation-bar:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label-md}"
  navigation-indicator:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
  widget:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.xl}"
    padding: "{spacing.md}"
  page:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.on-surface}"
  divider:
    backgroundColor: "{colors.outline-variant}"
    height: 1px
  page-dark:
    backgroundColor: "{colors.dark-background}"
    textColor: "{colors.dark-on-surface}"
  widget-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-on-surface}"
  card-dark:
    backgroundColor: "{colors.dark-surface-container-low}"
    textColor: "{colors.dark-on-surface}"
  diary-note-dark:
    backgroundColor: "{colors.dark-surface-container-lowest}"
    textColor: "{colors.dark-on-surface}"
  calendar-day-dark:
    backgroundColor: "{colors.dark-surface-container}"
    textColor: "{colors.dark-on-surface}"
  quick-entry-bar-dark:
    backgroundColor: "{colors.dark-surface-container-high}"
    textColor: "{colors.dark-on-surface}"
  chip-dark:
    backgroundColor: "{colors.dark-surface-container-highest}"
    textColor: "{colors.dark-on-surface}"
  navigation-bar-dark:
    backgroundColor: "{colors.dark-surface-container-low}"
    textColor: "{colors.dark-on-surface-variant}"
  button-primary-dark:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-on-primary}"
  button-tonal-dark:
    backgroundColor: "{colors.dark-primary-container}"
    textColor: "{colors.dark-on-primary-container}"
  badge-income-dark:
    backgroundColor: "{colors.dark-secondary}"
    textColor: "{colors.dark-on-secondary}"
  chip-income-dark:
    backgroundColor: "{colors.dark-secondary-container}"
    textColor: "{colors.dark-on-secondary-container}"
  badge-streak-dark:
    backgroundColor: "{colors.dark-tertiary}"
    textColor: "{colors.dark-on-tertiary}"
  badge-trip-dark:
    backgroundColor: "{colors.dark-tertiary-container}"
    textColor: "{colors.dark-on-tertiary-container}"
  divider-dark:
    backgroundColor: "{colors.dark-outline-variant}"
    height: 1px
  field-outline-dark:
    backgroundColor: "{colors.dark-outline}"
    height: 1px
---

# Oinky

## Overview

Oinky is a pocket diary that happens to keep your money straight. The UI should feel like a
**scrapbook kept in a piggy bank**: warm cream paper, soft piggy pink, rounded "squishy" shapes, and
stickers that look hand-placed. It is cute but calm — something you open every evening to jot a
line, not a dashboard that shouts. Money is always legible and honest; cuteness never hides a number.

Audience: young adults in Singapore and Malaysia who travel, spend in several currencies and like
journaling. The emotional target is *cosy, forgiving and a little bit delighted*.

## Colors

A warm, low-glare palette on cream paper with one confident accent.

- **Piggy Pink (primary, #C2356A):** the single accent for the most important action on a screen
  (save, add, today). Its light **Blush** container (#FFD9E3) marks selection: today's date, the
  chosen mood, the active tab.
- **Mint Money (secondary, #2B7A57):** income and positive balances only. Never used for decoration.
- **Coin Gold (tertiary, #855A00 / container #FFDC7A):** trips, streaks and small celebratory badges.
- **Cream Paper (neutral/surface, #FFF8F1):** the page. Cards are pure white (#FFFFFF) so they read
  as paper laid on paper; tinted "containers" step down to #F0DAD0 for wells and bars.
- **Cocoa Ink (on-surface, #3A2A2E):** all body text and expense amounts. **Dusty Rose**
  (#6E565C) for secondary text.
- **Dark theme:** the `dark-*` tokens mirror each role on a deep cocoa page (#211A1C) with pink
  lightened to #FFB1C8. Every text/background pair in both themes meets WCAG AA.

The brand palette is the default. Material "wallpaper" dynamic colour is an opt-in setting only.

## Typography

Two rounded families keep everything soft and friendly.

- **Fredoka** (SemiBold/Medium) for display, headlines and screen titles: bubbly and warm, used
  sparingly for dates ("Tuesday 29"), trip names and big numbers' labels.
- **Nunito** for everything you read or type: diary notes use `body-lg` (17/26) for comfortable
  journaling; UI text uses `body-md`; labels are bold `label-*`.
- **Amounts** use Nunito ExtraBold/Bold with tabular numerals (`amount-lg`, `amount-md`) so columns
  of money line up. Expenses are cocoa ink; income is mint with a leading "+".

## Layout

Single-column phone layout with a **16px gutter** and an 8px rhythm (4px half-step). Related
content is grouped into cards with 16px internal padding; sections are separated by 24px and led by
a Fredoka title. Primary actions sit at the bottom within thumb reach (FABs, the quick-entry bar).
All tap targets are at least 48px.

## Elevation & Depth

Flat and papery. Depth comes from **tonal layers**, not shadows: cream page → white cards →
tinted wells. Only floating elements (FAB, bottom sheets, the widget's quick-entry card) get a
soft, low shadow. Stickers may carry their own drop shadow as part of the image.

## Shapes

Chunky and rounded everywhere — nothing sharp. Cards 24px, hero cards and the widget 32px, inputs
and calendar days 16px, chips, buttons and the quick-entry bar fully rounded (pill). Photos use 16px
corners; stickers keep their own cut-out silhouette and get a gentle random tilt (±10°).

## Components

- **Buttons:** pill-shaped. Filled Piggy Pink for the one primary action; tonal Blush for secondary
  actions; text buttons for low-emphasis actions like Cancel.
- **Cards:** white on cream, 24px corners, no border, no shadow. The month summary is a Blush
  **hero card** with the average mood face and the month's spend in `amount-lg`.
- **Calendar day:** 16px rounded tile on `surface-container-low`, heat-tinted with pink as spending
  grows; today is a Blush tile. The day's mood face (emoji or sticker) is centred, with the day's
  spend below in `label-sm`, and a flag in the corner on trip days.
- **Mood faces:** 40px; selected face sits on a Blush rounded tile. Faces are user-customisable
  (emoji or sticker) — layouts must never assume they're emoji.
- **Stickers:** 88px on day pages, placed in a loose row with a small tilt; cut-outs keep
  transparency.
- **Diary note:** a white card with `body-lg` text and no visible border, like a page.
- **Quick-entry bar:** a pill on `surface-container-high` pinned above the keyboard with a live
  preview chip above it.
- **Chips:** pill chips on `surface-container-high`; selected chips turn Blush.
- **Reminder banners:** rose `error-container` cards with a friendly sentence and two text actions.
- **Trip badge:** coin-gold pill with a flag, e.g. "🇯🇵 Day 3".
- **Navigation bar:** cream-tinted bar, Blush pill indicator behind the active icon.
- **Widget:** cream card with 32px corners; pink quick-entry pill; mood faces row.
- **Dark variants:** every component has a `*-dark` twin built from the `dark-*` colour tokens; the
  app switches with the system theme.

## Do's and Don'ts

- Do keep Piggy Pink for one primary action per screen; use Blush for selection.
- Do show every foreign amount with its main-currency equivalent ("RM135 ≈ S$40.12").
- Do use mint only for income and positive numbers — never for decoration.
- Do use tabular `amount-*` styles for any money.
- Don't use pure black text or pure white pages; use Cocoa Ink on Cream Paper.
- Don't add heavy shadows or outlines to cards; separate with tone and space.
- Don't assume mood faces are emoji — they can be stickers.
- Don't use more than two weights on a screen besides amounts.
