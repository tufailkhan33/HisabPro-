# HisabPro Windows 10/11 64-bit Build & Installer Guide (`HisabPro_Setup.exe` + `HisabPro_Windows_Complete.zip`)

This workspace contains three coordinated Gradle modules plus automated Windows packaging:
- `:shared` — Pure Kotlin models, Paisa-safe `SharedFinancialEngine`, PKT calendar enforcement, PBKDF2 hashing, rate limiter, and backup JSON builder.
- `:app` — Existing production Android app **HisabPro v1.3** (`com.aistudio.hisabpro.m4k9tx`), preserved 100% intact.
- `:desktopApp` — Compose Multiplatform Windows Desktop application sharing the exact Firebase project (`gen-lang-client-0095595188`) and named Firestore database (`ai-studio-android-hisabpro-2c926fa7-5e95-49f4-a400-e38f863df997`), plus local Windows SQLite WAL storage and A4 PDF / `.xlsx` / CSV / Native Print engines.
- `.github/workflows/build-windows-installer.yml` — Automated `windows-latest` CI workflow that tests, builds `HisabPro_Setup.exe`, verifies the PE header, stages `HisabPro_Windows_Release/`, and produces `HisabPro_Windows_Complete.zip`.

## Prerequisites on Windows 10 / 11 (64-bit)
1. **JDK 21 (64-bit)** (Temurin 21 recommended)
2. **Gradle 9.3.1** (`gradle` on `PATH`)
3. **NSIS 3.x** (`makensis` on `PATH`) for building `HisabPro_Setup.exe`

## Automated Option 1 — GitHub Actions (`windows-latest` Runner)
1. Push this repository to GitHub (branches `main` or `master`, or trigger **Actions -> Build HisabPro Windows Installer -> Run workflow**).
2. Download the artifact **`HisabPro_Windows_Complete`** (`HisabPro_Windows_Complete.zip`), which contains ONLY:
   - `HisabPro_Setup.exe`
   - `README.txt`
   - `LICENSE.txt`

## Automated Option 2 — One-Click Windows 10/11 PowerShell Script
On a Windows 10/11 64-bit PC with JDK 21, Gradle, and NSIS installed, run:
```powershell
powershell -ExecutionPolicy Bypass -File desktopApp\installer\Build_Windows_Release.ps1
```
This runs `:shared:test` and `:desktopApp:test`, builds `desktopApp\build\compose\binaries\main\app\HisabPro\`, compiles `desktopApp\installer\HisabPro_Setup.exe`, stages a clean Windows-only `HisabPro_Windows_Release\` directory (excluding all Android source/APK/build files), and compresses it into `HisabPro_Windows_Complete.zip` in the workspace root.

## Manual Step-by-Step Windows Commands
```bat
gradle :shared:test :desktopApp:test --console=plain
gradle :desktopApp:createDistributable --console=plain
makensis desktopApp\installer\HisabPro.nsi
```

## Optional Google OAuth2 Desktop Client Override
By default, `FirebaseDesktopConfig` reads the shared project configuration from `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`.
If your Google Cloud OAuth consent screen requires a dedicated **Desktop App OAuth Client ID** for local browser loopback redirects (`http://127.0.0.1:<port>/callback`), set environment variables (or add to `.env` locally, never commit secrets):
```env
HISABPRO_DESKTOP_GOOGLE_CLIENT_ID=<your-desktop-oauth-client-id>
HISABPRO_DESKTOP_GOOGLE_CLIENT_SECRET=<your-desktop-oauth-client-secret>
```

## Local Windows Storage Path
Offline SQLite database (`hisabpro_desktop.db`) and AES-256-GCM encrypted session vault are stored at:
`%LOCALAPPDATA%\HisabPro\`
