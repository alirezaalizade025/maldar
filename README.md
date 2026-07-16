# Maldar (Android)

A fully offline personal accounting app: manual + SMS-detected expense/income tracking,
loan due-date reminders, bank account balances, and monthly reports.

## How the 5 features map to the code

| # | Feature | Where |
|---|---------|-------|
| 1 | Add daily spend via bank SMS, with a dynamic sender list and a confirm-before-save step | `sms/SmsReceiver.kt`, `sms/SmsParser.kt`, `ui/screens/SmsConfirmationScreen.kt`, `ui/screens/BankAccountsScreen.kt` (Add SMS Sender) |
| 2 | Loan details + reminder before due date | `data/Entities.kt` (`LoanEntity`), `ui/screens/LoansScreen.kt`, `worker/LoanReminderWorker.kt` |
| 3 | Reports from daily data | `ui/screens/ReportsScreen.kt` |
| 4 | Add incomes | `ui/screens/AddTransactionScreen.kt` (Income/Expense toggle) |
| 5 | Bank accounts + balances, linked to SMS | `ui/screens/BankAccountsScreen.kt`, `data/FinanceRepository.kt` (`adjustBalance`) |

## Categories

Categories (Food, Transport, Salary, etc.) are stored in the database, not hardcoded.
A default set is seeded the first time the app runs (`data/AppDatabase.kt`), and from
then on you can add, rename, or delete categories from the **"Manage categories…"**
option at the bottom of any category dropdown (`ui/screens/CategoryPicker.kt`) — used
consistently in both Add Transaction and the SMS confirmation screen.

## Opening the project

1. Install **Android Studio** (Koala or newer).
2. `File > Open` → select the `PersonalAccountingApp` folder.
3. Android Studio will offer to generate the Gradle wrapper jar automatically on
   first sync (this project ships `gradle-wrapper.properties` but not the binary
   jar, since it can't be downloaded in this environment). Just click through
   the sync prompt — it needs internet access once, to pull Gradle + dependencies.
4. Run on a device or emulator with **API 26+**. SMS parsing only works on a
   real device (or an emulator you send test SMS to via `adb emu sms send`),
   since emulators without a SIM don't receive real bank SMS.

## Important: about the SMS permission

`READ_SMS` / `RECEIVE_SMS` are **restricted on the Play Store** to a small set
of app categories (default SMS app, etc.) — a personal finance app doesn't
qualify. This project is built for **sideloading** (installing the APK
directly, e.g. via `adb install` or transferring the file to your phone), which
has no such restriction. If you ever want to distribute this more broadly,
you'd need to switch to a Notification Listener approach instead of direct SMS
reading (Play Store does allow that permission), or keep it as a personal-use APK.

## How the SMS flow works

1. Go to **Accounts** tab → add a bank account → add an **SMS Sender** (the
   exact sender ID shown in your Messages app, e.g. `HDFCBK`, `VM-SBIINB`, or
   a phone number), linked to that account.
2. When an SMS arrives from a watched sender, `SmsReceiver` checks it with a
   generic regex parser (`SmsParser`) that looks for currency markers
   (`Rs.`, `INR`, `₹`) and debit/credit keywords. It does **not** auto-save —
   it creates a "pending" entry and fires a notification.
3. Tapping the notification (or the banner on the Dashboard) opens the
   **Confirm SMS Transactions** screen, where you review/edit the amount,
   type, and category before it's saved. This "stay for confirm" step exists
   because generic parsing across many bank formats will occasionally
   misread a message — better to catch it here than to silently miscount.
4. Once confirmed, the transaction is saved and the linked bank account's
   balance is updated automatically.

## Loan reminders

`LoanReminderWorker` runs once a day via WorkManager and checks every active
loan's due date against its `reminderDaysBefore` setting, firing a local
notification when you're inside that window.

## Notes / next steps you may want

- The category list in Add Transaction is a fixed set — easy to make
  user-editable later (`AddTransactionScreen.kt`, `expenseCategories`/`incomeCategories`).
- Reports currently show one month at a time with category bars; a
  year-over-year view would be a natural next addition.
- No cloud sync/backup is implemented — everything lives in a local Room
  database (`maldar.db`), private to the app.
