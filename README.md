# Reckon — shared expenses, properly settled

A native Android app for splitting costs in **messy groups** — the cases Splitwise
handles badly:

- **Partial participation** — "six families on a trip, but the Mehtas skipped dinner."
- **Mixed currencies** — log a hotel in EUR and a taxi in THB; it converts everything
  to your base currency using **live, real exchange rates**.
- **Per-head vs per-family** splitting — fair when families are different sizes.
- **Minimum settle-up** — reduces everyone's balances to the fewest possible payments.

Add expenses in **plain English** — "Dinner was ₹6,000, I paid, everyone except the
Mehtas" — and the app fills in the amount, who paid, who shared, and the split.

## How the "AI + real data" parts actually work

- **Live FX rates** come from a free, keyless public exchange-rate service, fetched over
  the internet. Tap **fetch live** and it converts everything. No signup, no key. (You can
  also type any rate in by hand.)
- **Plain-English parsing** works out of the box with a built-in parser — no key, no
  network needed. For sharper parsing of unusual phrasing, you can paste your own
  **Anthropic API key** under **⚙ API key**; it's stored only on your device and, when
  present, the app sends the sentence to Claude to structure it. If the key is absent or a
  call fails, it silently falls back to the built-in parser, so the app always works.

Everything is stored locally on the device. The only network calls are the FX fetch and
(optionally) the AI parse.

---

## Build the APK on GitHub (same flow as before)

1. **Create a new repository** on GitHub (private is fine).
2. **Upload the project**, keeping folder structure — including the hidden `.github`
   folder. With git:
   ```bash
   cd reckon-android
   git init
   git add .
   git commit -m "Reckon"
   git branch -M main
   git remote add origin https://github.com/<your-username>/reckon.git
   git push -u origin main
   ```
   (If you drag-and-drop on the website instead, recreate the workflow by hand if `.github`
   doesn't upload: **Add file → Create new file**, name it
   `.github/workflows/build-apk.yml`, and paste in the contents of that file.)
3. Open the **Actions** tab and wait for **Build APK** to finish (~3–5 min, green check).
4. Open the run → **Artifacts** → download **reckon-apk** → unzip → `app-debug.apk`.

## Install on your phone

Copy `app-debug.apk` to your phone, tap it, allow "install from unknown sources," install.
It's a debug build signed with Android's debug key — perfect for installing on your own
devices.

---

## Using it

1. **Add your group** under *Group*. Give a family a "people" count (e.g. the Mehtas = 4).
   Tap a member chip to mark **(me)** — that's who "I paid" refers to.
2. **Add an expense**: type a sentence and hit **Add with AI**, or use **enter manually**
   for full control (who paid, who shared, per-head vs per-family).
3. **Settle in** your chosen currency (top of screen). Tap **fetch live** to pull current
   rates for any foreign currencies you've used.
4. The **settlement** card shows each member's balance and the minimum set of payments.

---

## Make it yours

- App name: `app/src/main/res/values/strings.xml`
- Colours/theme: `app/src/main/java/com/reckon/ui/theme/Color.kt`
- Currencies offered: `currencies` list in `ui/ReckonViewModel.kt`
- Parsing rules (no-key): `engine/NlParser.kt`
- Rate source: `data/FxRepository.kt`
- AI model used with your key: `MODEL` in `data/AiParser.kt`
- Package name: `com.reckon` (change `namespace`/`applicationId` in `app/build.gradle.kts`
  and the folders if you rebrand).

## Versions (pinned for reliable CI)

AGP 8.5.2 · Gradle 8.7 · Kotlin 1.9.24 · Compose Compiler 1.5.14 · Compose BOM 2024.06.00 ·
compileSdk/targetSdk 34 · minSdk 26 (Android 8.0+) · JDK 17

## Note on accuracy

The built-in parser is good at common phrasings but not infallible — glance at the split
it chose before trusting it (you can delete and re-add, or use the manual form). Live rates
are mid-market reference rates, fine for splitting trips; they aren't the exact rate your
bank charged.
