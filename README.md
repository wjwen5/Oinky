# Oinky 🐷: a diary with a wallet

An Android app (Kotlin + Jetpack Compose) that works like a diary. Each day you pick a mood emoji, write a note, add photos, and log what you spent. All amounts are converted to one main currency using online exchange rates.

## Features

| Requirement | How it works |
|---|---|
| **Diary-style month/week view** | The month grid shows a mood emoji per day, what you spent that day, and a colour tint that gets stronger on heavier spending days. The week view shows each day as a diary card with the note preview, a photo thumbnail, and spending and income. |
| **Day page** | Mood picker (🤩 😊 😐 😔 😫), a notes field that saves itself, a photo strip, and the day's money entries. |
| **Quick entry** | Type `rm135 on dinner` and you get 🍜 Food · RM135 ≈ S$40.12. The preview updates as you type, and pressing Enter saves the entry. You can undo or edit it from the snackbar. |
| **Multi-currency** | Each entry keeps its original amount and currency, plus the converted amount in your main currency. Latest rates come from [open.er-api.com](https://open.er-api.com), with [frankfurter](https://frankfurter.dev) (European Central Bank rates) as a fallback. Back-dated entries use the historical rate for that date. Rates are cached for offline use. Entries made offline get converted automatically once a rate is available. |
| **Recurring payments** | **Auto-record** rules add the transaction for you (rent, Netflix). You can also set **Repeat** on any entry when you save it. |
| **"You haven't recorded X" reminders** | **Remind me** rules have a grace period and match words. An occurrence counts as paid if an entry mentioning it falls anywhere from half a period early to just before the next due date. Overdue items show as banners on the calendar (**Record** / **Skip**) and as a notification at most once a day. |
| **Receipt OCR** | Take a photo or pick one from the gallery. ML Kit reads it on the device, the app rebuilds the rows so amounts line up with their labels, and a parser finds the total, currency, merchant, date and category. You then check and confirm the result in the editor. |

### Trips & world map

| | |
|---|---|
| **World map** | A map built into the app from Natural Earth country outlines (public domain), so it needs no Google Maps key and works offline. Countries you've visited are filled in, and every trip place gets a pin. Pinch to zoom, drag to pan, double-tap to zoom in. Tap a country to filter your trips to it; tap a pin to open that trip. |
| **Travel stats** | Countries, continents, trips and days away. Overlapping trips aren't counted twice, and your home country (worked out from your main currency) is excluded. |
| **Trips** | Each trip has a name, emoji, date range, local currency, optional budget, notes and cover photo. Places come from a country search (works offline) or a city search using the phone's built-in geocoder (needs internet). Adding the first place switches the trip's currency to that country's currency. |
| **Expenses per trip** | Any entry dated inside a trip is linked to it automatically, and you can change the trip in the editor. On trip days, quick entries default to the local currency, so `1500 ramen` in Japan is read as ¥1,500. The trip page shows total spent, spending per day, budget used and how much you can still spend per day, spending by category, and the currencies you paid in. |
| **Travel journal** | Each trip day shows its mood, note, spending and photos. The calendar marks trip days with the country's flag, and a day's page shows "Day 3 of Japan". |
| **Photos** | Diary photos from trip days collect in the trip's photo gallery. **Add photos** on a trip imports many at once and files each one on the day it was taken (from the photo's date data). |

### Home-screen widget 🐷

A resizable 4×2 widget (Settings → **Add to home screen**, or long-press the home screen → Widgets → Oinky) shows:
- today's total spent and number of entries, plus a flag and day number when you're on a trip;
- a **✏️ rm135 on dinner…** pill that opens a small quick-entry card over the home screen, with a live preview; press Enter and it saves and closes;
- five mood emojis: tap one to set today's mood, tap it again to clear it.

It updates straight away whenever you add entries, and at midnight.

Extras already built in:
- **Learns your categories.** If you change a suggested category, the app remembers that phrase or merchant for next time.
- **Spots recurring patterns.** If you log something like "haircut" about once a month by hand, the Recurring tab offers to track it.
- **Mood × money insights.** Shows your average daily spending for each mood, with a headline such as "You spend 2.3× more on low-mood days". It also shows spending by category and by original currency.
- **Evening nudge.** After 8pm, if today's diary is still empty, you get a reminder.

### Quick-entry grammar

```
rm135 on dinner            → MYR 135, Food
135rm dinner / RM 13.50    → currency before or after the number
S$4.20 kopi, ¥1,500 ramen  → symbols, thousands separators
hotel 1.2k yen             → k suffix
$12 movie                  → $ = main currency if it's a dollar, else USD
rmb200 taobao              → CNY (rmb is not rm)
salary 5000 / +50 from mum → income
yesterday taxi 18          → dates: today, yesterday, 3 days ago, last fri, 12/3, 2026-03-12
coffee 6.8 @starbucks      → merchant (also "at starbucks")
135 on dinner in ringgit   → currency word anywhere
```

## Architecture

```
core/   Pure Kotlin/JVM, unit-tested (35 tests)
  QuickEntryParser   free text → amount/currency/category/date/merchant
  CategoryClassifier keyword table (MY/SG merchants included) + learned overrides
  Currencies/RateTable  alias resolution, cross-rate conversion, formatting
  Schedule, MissedPaymentDetector, RecurrenceDetector
  ReceiptParser      OCR text → total/currency/merchant/date
  MoodInsights       spending by mood + headline
  WorldMapData, Mercator, Countries   map outlines, hit-testing, flags, local currency
  TripMath           trip stats, budget pace, travel summary, photo-to-day filing
app/    Android
  data/     Room (days, photos, transactions, recurring rules, rates, learned categories)
  rates/    ExchangeRateRepository (HTTP + cache + historical)
  ocr/      ML Kit text recognition, rebuilds rows from bounding boxes
  assets/world_50m.txt  simplified country outlines (278 KB, 239 countries)
  work/     DailyWorker (rates, offline conversions, auto-record, missed reminders, diary nudge)
  ui/       Compose screens: calendar, day, recurring, insights, settings
```

Money is stored as fixed-point hundredths (`Long`), with both the original amount and the main-currency amount kept. When you change the main currency, every entry is converted again from its original amount, so switching back loses nothing.

## Build & run

Requirements: Android Studio (Ladybug or newer) or JDK 17+ with the Android SDK (API 35).

```bash
./gradlew :core:test          # parser / recurrence / receipt / map / trip tests (no Android needed)
./gradlew :app:installDebug   # build and install on a device/emulator
```

Minimum Android version is 8.0 (API 26).

---

## Similar apps & how this could be better

**What already exists**
- **Journals that also track money:** [Daybook](https://play.google.com/store/apps/details?id=com.bigheadtechies.diary) (has an expense tracker), [Universum](https://www.androidauthority.com/best-diary-apps-journal-apps-android-892375/) (expenses inside entries), [Diarium](https://play.google.com/store/apps/details?id=partl.Diarium) (brings in calendar and fitness data), [Daylio](https://daylio.net/) (the reference for mood emoji calendars).
- **Mood × money apps:** [Mindspend](https://play.google.com/store/apps/details?id=ro.soloventures.mindspend), [Mood Money / Feelance](https://play.google.com/store/apps/details?id=com.tracker.emotional) (a "spending vs feeling" heatmap), [Plong](https://play.google.com/store/apps/details?id=com.plong.plong), [feelwrite](https://feelwrite.app/).
- **Natural-language entry and OCR:** [GreenBills](https://apps.apple.com/id/app/greenbills-ocr-expense-tracker/id6758046297) ("Coffee 32 Alipay"), [TrackMyExpense](https://trackmyexpense.app/) (voice/AI entry, 150+ currencies), [ReceiptIA](https://play.google.com/store/apps/details?id=com.optiss.aireceipts).

**Gaps this app fills:** the journal apps treat money as an add-on. The finance apps treat mood as a tag. None of them handle a regional traveller's multi-currency life well, for example living in Singapore, spending in Malaysia, and typing "rm135".

**Suggested improvements (roughly by value / effort)**
1. **Log spending at the moment you pay.** Read bank/e-wallet payment notifications (DBS, Maybank, GrabPay, TNG) with a `NotificationListenerService` and prefill an entry. This removes most manual typing.
2. **Voice entry.** Speech-to-text into the same quick-entry parser ("forty ringgit grab to KLCC").
3. **LLM fallback for the parser.** Keep the fast offline rules, and send only the entries they can't parse (or messy receipts) to an LLM to extract structured data, with the user's permission.
4. **Spending "why" tags.** A one-tap reason on each purchase (bored / treat / need / social) turns mood × money into advice you can act on, which is the core idea of Mindspend.
5. ~~Trips~~ (done). Next steps: offline map tiles or a city-level map (osmdroid/MapLibre) for zooming into a trip, a route line joining places in order, and a shareable trip recap image.
6. **Budgets and gentle limits**, per category and per mood ("you tend to overspend on 😔 days, want a soft cap?").
7. ~~Home-screen widget~~ (done). Next: receipt scan and voice entry from the widget.
8. **On This Day.** Resurface past entries and photos, as Day One and Daylio do, so people come back to the app.
9. **Privacy and backup.** Biometric lock, encrypted export, backup to Google Drive, and CSV/QIF export for spreadsheets.
10. **Split bills** with friends, with amounts owed by each person in their own currency.
11. **Richer OCR.** Line items, tax, and card last-4 digits. Cloud OCR for receipts in Chinese or Japanese (the bundled ML Kit model is Latin-only; add `text-recognition-chinese` / `-japanese`).
