# SplitWise (Android)

A Splitwise-style expense sharing app for Android, built with Kotlin, Jetpack Compose and Room. Works offline; all data is stored on-device.

## Features
- Create groups and add members
- Add expenses, choose who paid and split **equally**, by **exact amounts** or by **percentage**
- Per-member balances (who owes / is owed)
- Simplified debts: minimal set of payments to settle up
- One-tap "Settle" records a payment
- Delete expenses / groups

## Structure
- `domain/` – pure Kotlin: split calculators, balances, debt simplification (unit tested)
- `data/` – Room entities, DAO, database
- `ui/` – ViewModel + Compose screens

## Build
Open in Android Studio (Koala+), or run `./gradlew assembleDebug` / `./gradlew test` with the Android SDK installed.
Money is stored in cents (`Long`) to avoid floating point errors.

## Ideas for next steps
Friends outside groups, expense categories/notes, currencies, cloud sync + auth, receipts, export to CSV.
