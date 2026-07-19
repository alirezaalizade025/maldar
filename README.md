# Maldar (مل der / مال‌دار) — Android

A fully offline personal accounting app (Farsi / RTL, Toman currency, Jalali
calendar): manual + SMS/notification-detected expense/income tracking, loan
due-date reminders, bank account balances, and monthly reports.

## Features → code map

| # | Feature | Where |
|---|---------|-------|
| 1 | Capture bank transactions from SMS **or** notifications, with a dynamic sender list and a confirm-before-save step | `sms/SmsReceiver.kt`, `sms/BankNotificationListenerService.kt`, `sms/SmsParser.kt`, `ui/screens/SmsConfirmationScreen.kt`, `ui/screens/BankAccountsScreen.kt` |
| 2 | Loan details + reminder before due date | `data/Entities.kt` (`LoanEntity`), `ui/screens/LoansScreen.kt`, `worker/LoanReminderWorker.kt` |
| 3 | Reports (month totals + category bars + income/expense/balance trend) | `ui/screens/ReportsScreen.kt`, `ui/screens/MonthTrendGraph.kt` |
| 4 | Manual income/expense entry + edit/delete | `ui/screens/AddTransactionScreen.kt`, `ui/screens/DashboardScreen.kt` (edit/delete dialogs) |
| 5 | Bank accounts + balances, linked to SMS/notification | `ui/screens/BankAccountsScreen.kt`, `ui/screens/AccountSmsScreen.kt`, `data/FinanceRepository.kt` (`adjustBalance`) |
| 6 | Daily reminder notification | `ui/screens/SettingsScreen.kt`, `worker/DailyReminderWorker.kt` |
| 7 | Data export / import (CSV + JSON backup) | `ui/screens/ExportScreen.kt`, `ui/screens/ImportScreen.kt`, `util/DataExport.kt` |
| 8 | In-app update checker (auto-check on launch, skip version) | `util/UpdateChecker.kt`, `ui/screens/UpdateDialogs.kt` |
| 9 | Crash log viewer (share/clear) | `ui/screens/CrashLogScreen.kt`, `util/CrashLogger.kt` |

## Navigation

Bottom bar: **Home / Add / Loans / Reports / Accounts**. The top-right menu
(⋮) opens: check for updates, crash log, export, import, settings, about.

## Categories

Categories (Food, Transport, Salary, etc.) are stored in the database, not
hardcoded. A default set is seeded the first time the app runs
(`data/AppDatabase.kt`). From then on you can add, rename, or delete categories
from the **"Manage categories…"** option in any category dropdown
(`ui/screens/CategoryPicker.kt`), used consistently in Add Transaction and the
SMS/notification confirmation dialog. Deleting a category that is still used by
transactions automatically reassigns those records to "سایر" so nothing is lost.

## Capture path: SMS vs Notification Listener

`READ_SMS` / `RECEIVE_SMS` are **restricted on the Play Store** to a small set
of app categories (default SMS app, etc.) — a personal finance app doesn't
qualify.

- **SMS Receiver** (`SmsReceiver`) works when the app is **sideloaded** (APK
  installed directly via `adb install` or file transfer). It has no Play Store
  restriction in that context.
- **Notification Listener** (`BankNotificationListenerService`) is the
  **Play-Store-compatible** alternative. It reads bank transaction
  *notifications* (which banks post for every card/account event) without the
  restricted SMS permission. The user enables it in system settings
  (Settings ▸ Notifications ▸ Maldar).

Whichever path is active, nothing is saved automatically: a "pending" entry is
created and the user confirms it on the Confirm screen. Both paths share the
same `SmsParser` (looks for currency markers `تومان`, `ریال`, `Rls`, `IRR`,
`toman`, and debit/credit keywords) and the same pending/confirm flow in
`SmsConfirmationScreen`.

## How the capture flow works

1. Go to **Accounts** → add a bank account → add an **SMS Sender** (the exact
   sender ID shown in your Messages app, e.g. `HDFCBK`, `VM-SBIINB`, or a phone
   number), linked to that account. The sender picker can also **detect recent
   bank senders** from your SMS inbox (`util/SmsInboxReader.recentSenders`) so
   you don't have to type the ID manually.
2. When an SMS arrives from a watched sender, or a matching bank notification is
   posted, the parser extracts amount/type/balance and creates a "pending" entry
   + fires a notification. It does **not** auto-save.
3. Tapping the notification (or the banner on the Dashboard) opens **Confirm SMS
   Transactions**, where you review/edit amount, type, and category before
   saving. This "stay for confirm" step exists because generic parsing across
   many bank formats will occasionally misread a message — better to catch it
   here than to silently miscount. (Pending items can also be opened/edited
   directly from the Dashboard banner, and reviewed/ignored/deleted from the
   "Reviewed" section.)
4. Once confirmed, the transaction is saved and the linked bank account's balance
   is updated automatically via `FinanceRepository.adjustBalance`.

## Account SMS reconciliation

Inside a bank account you can open **Show SMS** (`AccountSmsScreen`) which lists
the recent inbox SMS for that account's senders (parsed amount/type + Jalali
date) and marks each as reconciled or not against your saved transactions. Tap a
message to prefill the Add Transaction screen — handy for backfilling historical
spend from your bank's messages.

## Loan reminders

`LoanReminderWorker` runs once a day via WorkManager and checks every active
loan's due date against its `reminderDaysBefore` setting, firing a local
notification when you're inside that window.

## Daily reminder

`DailyReminderWorker` fires a daily summary notification at a user-chosen hour
(configured in **Settings**) and reschedules itself. Disabling it cancels the
pending work. A `BootReceiver` re-arms WorkManager after reboot.

## Backup / export / import

From the top-right menu → **Export** you can share all data as CSV or JSON
(`util/DataExport`). **Import** restores a JSON backup (also triggered
automatically if you tap a `.json` backup from a file manager — see the
`VIEW`/`BROWSABLE` intent filter in `AndroidManifest.xml`).

## Reports

One month at a time (navigate back up to 12 months, forward to current month).
Shows income/expense/net, a per-category bar breakdown (toggle amount ↔
percent), and a 6-month trend graph with income, expense, net, and **balance
over time** lines (`MonthTrendGraph`).

## Opening the project

1. Install **Android Studio** (Koala or newer).
2. `File > Open` → select the `PersonalAccountingApp` folder.
3. Android Studio will offer to generate the Gradle wrapper jar automatically on
   first sync (this project ships `gradle-wrapper.properties` but not the binary
   jar, since it can't be downloaded in this environment). Just click through
   the sync prompt — it needs internet access once, to pull Gradle + dependencies.
4. Run on a device or emulator with **API 26+**. SMS parsing only works on a real
   device (or an emulator you send test SMS to via `adb emu sms send`), since
   emulators without a SIM don't receive real bank SMS. The Notification Listener
   works on any device/emulator that receives the bank's notifications.

## Versioning & release

`app/build.gradle.kts` computes `versionName` / `versionCode` from conventional
commits since the last `vX.Y[.Z]` tag (a `feat` bumps MINOR and resets PATCH; a
`fix` bumps PATCH; `versionCode` is monotonic). `.github/workflows/build-apk.yml`
builds the release APK and publishes a GitHub **prerelease** tagged by the
resolved `versionName`. The update checker compares `versionCode` (Int), not
parsed `versionName`. Bump is handled automatically by the commit convention —
no manual edit of `versionName`/`versionCode` is needed.

## Notes / next steps you may want

- Categories are fully user-editable from the **"Manage categories…"** option
  in any category dropdown; deleting a used category reassigns records to "سایر".
- Transaction edit/delete is exposed on the Dashboard; `deleteTransaction` calls
  `adjustBalance`, so removing a record keeps account balances correct.
- Reports show one month at a time with category bars plus a net/balance marker;
  a year-over-year view would be a natural next addition.
- Hand-rolled date converters (Jalali) have unit tests around leap years /
  boundaries in `app/src/test/java/com/personalfinance/tracker/util/JalaliCalendarTest.kt`.
