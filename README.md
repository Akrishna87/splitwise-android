# Splitzy (Android)

A full-featured Splitwise-style expense sharing app. Kotlin, Jetpack Compose, Room. Works offline; data lives on the phone.

## Features
**Core**
- Groups and **friends** (1:1 balances outside groups), overall balance on the home screen
- Add / **edit** / delete expenses, with date, **categories**, notes and **comments**
- Six split types: **equal, exact, percent, shares, adjustments, itemized**
- Balances, **simplified debts** (toggle to see direct who-owes-whom), partial and full **settle up**

**Pro-style extras**
- **Receipt scanning**: take or pick a photo, on-device OCR (ML Kit, works offline) extracts line items and total; assign items to people; tax/tip is shared proportionally. Receipt photo is kept with the expense
- **Multi-currency**: each expense in its own currency with live ECB rates (editable, cached for offline); balances in the group currency
- **Recurring expenses** (daily/weekly/monthly/yearly) created automatically by a background worker
- **Reminders**: daily notification when you have unsettled balances
- **Search** (per group and global) and category filter
- **Charts**: spending by category, last 6 months, who paid, your share
- **Export CSV**; **pay links** for UPI (INR), PayPal.me and Venmo from the settle-up dialog
- **Activity feed** across all groups

**Sharing between phones (no server needed)**
Open a group → *Share group (file)* → send the `.splitwise.json` over WhatsApp/email/Drive. Your friend taps *Import*.
Every record has a UUID, timestamp and soft-delete flag, so importing **merges** (newest edit wins, deletions propagate) and you can keep exchanging updates. *Export backup / Import file* in the Account tab does the same for all data.

## Not included
Live cloud sync / accounts (would need a backend such as your own Firebase project), bank-statement import, and Splitwise's in-house payment network.

## Structure
- `domain/` pure Kotlin, unit tested: money, splits, balances, debt simplification, stats, CSV, receipt parser, sync/merge, recurrence, pay links
- `data/` Room entities/DAO, repository, settings, rates, OCR
- `work/` WorkManager daily job and notifications
- `ui/` Compose screens and ViewModel

## Build
CI (`.github/workflows/ci.yml`) runs the unit tests and builds the debug APK on every push; download it from the run's *app-debug-apk* artifact.
Locally: Android Studio, or `./gradlew testDebugUnitTest assembleDebug`.
