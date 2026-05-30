# Codex Startup Checklist

This project uses daily progress notes and date-stamped debug APK names. At the start of every Codex session:

1. Confirm today's date from the environment.
2. Read the latest `docs/YYYY-MM-DD-progress.md`.
3. Check `app/build.gradle.kts` for `versionName` and the `copyDatedDebugApk` output name.
4. If today's progress note does not exist, create `docs/YYYY-MM-DD-progress.md` from the latest note's status.
5. Keep the debug APK copy name in this format: `Counseling_MM_DD_vX.Y.Z_debug.apk`.
6. If behavior changes, update `versionName` according to the scope of the change. If only docs or notes change, keep the app version.
7. Before continuing feature work, scan the latest note's "next work" section and verify whether the existing code already covers any item.

Current handoff as of 2026-05-29:

- Latest completed progress note: `docs/2026-05-28-progress.md`
- Current app version: `0.4.1`
- Last generated dated APK: `app/build/outputs/apk/debug/Counseling_05_28_v0.4.1_debug.apk`
- Main next step: real-device testing for model load, image/audio attachments, Android speech recognition text input, session restore, important memory save/delete, and RAG-lite related-context injection.
- Secondary next steps: refine Health Connect prompt injection, multi-session management, important memory UI improvements, RAG-lite ranking, and date/version naming hygiene.

Build environment reminder:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```
