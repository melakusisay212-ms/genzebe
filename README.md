# ገንዘቤ (Genzebe)

Android app that reads bank and wallet SMS (CBE, CBE Birr, BoA, telebirr, M-PESA, eBirr) and keeps your income and expenses on the Ethiopian calendar. No manual entry, no internet permission, all data stays on the phone.

## Rules
- Money into an account is income. Money out, including paying people, is expense.
- Transfer is only between your own accounts and is not counted.
- A fee is part of the same expense. No categories.
- Week starts Monday. Dates are Ethiopian everywhere.

## Build on GitHub
1. Create a repo, add this file once through the GitHub website: `.github/workflows/build.yml` (copy from the file supplied next to the zip).
2. Upload `genzebe.zip` to the repo root. The workflow unpacks it and builds.
3. Open Actions, the latest run, and download `genzebe-apks` from Artifacts.
For each later update, upload a new `genzebe.zip` over the old one.

If a build fails, send the red log lines. Only the broken files need to be replaced.

## Two APKs
- **share**: no SMS permission. Google Play Protect does not flag it. You long-press a bank SMS, Share, ገንዘቤ (or paste it in Settings).
- **auto**: reads SMS by itself. Play Protect blocks apps with SMS permission when installed from a browser, chat or file manager. Signing does not change that. Options: tap More details, Install anyway; or turn off Play Protect scanning briefly; or publish through Google Play.
On Android 13+, an auto APK installed outside the Play Store needs Settings, Apps, ገንዘቤ, three dots, Allow restricted settings.

## Release key (so updates install over old versions)
```
keytool -genkeypair -keystore release.jks -alias genzebe -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks
```
Add repository secrets: `KEYSTORE_BASE64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and optionally `KEY_PASSWORD`. Keep release.jks safe. Without secrets the workflow uses a throwaway key and warns.

## Notes
- Sender IDs are not hard-coded; detection is by message wording per provider. Add real sender IDs to `KnownSenders` in `SmsParser.kt` once known.
- Some templates (transfers, M-PESA English, eBirr receive) are guesses. A balance-chain check flags a transaction as "Check this" when the running balance does not add up.
- Core calendar and parser tests: `gradle testShareDebugUnitTest`.
