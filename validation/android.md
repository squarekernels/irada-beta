# Irada Android validation — November 20, 2026

## Scope and source audit

The active checkout at `0fd518b` contained only the Android scaffold. That commit’s description mentions a running APK but does not include its sources. The foundation was present as uncommitted files in `/private/tmp/irada-android-v1` on `codex/android-v1`, based on `5c6aabb`. Those Android sources were copied into this checkout on the new `codex/android-stage-1` branch without modifying the original worktree. No commits or pushes were made.

The Irada stage table in `apps/android/README.md` and `DESIGN.md` govern this implementation. The root `ANDROID_PLN_REF.md` also contains unrelated film-catalog milestones; those are not evidence of Irada stage completion.

## Stage 0

- Gradle Wrapper `9.4.1`, AGP `9.2.0`, Kotlin/Compose compiler `2.3.20`, Compose Material 3 and Navigation 3 are configured. SDK 37, minSdk 26; application ID `com.ctxaware.irada`.
- Built the copied foundation from this checkout using `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Build and lint passed; unit test task had no sources.
- Initial connected run failed because the Extended FAB did not expose its visible label in merged semantics. Added the explicit “Add tasks” accessibility label; both foundation tests then passed with `./gradlew :app:connectedDebugAndroidTest`.
- Installed and launched using `android run --apks=apps/android/app/build/outputs/apk/debug/app-debug.apk` on `Medium_Phone_API_37.0`, Android 17/API 37, emulator-5554.
- Enabled airplane mode with `adb shell cmd connectivity airplane-mode enable`; Home and the Settings/capture shells remained accessible.
- Switched appearance with `adb shell cmd uimode night yes` / `no`; visually inspected light and dark Home. The purple functional plus and Irada branding render correctly. Adaptive launcher XML uses the Irada foreground and a purple background; the launch splash silhouette was also observed.
- Evidence: [light Home](android/stage0-home-light.png), [dark Home](android/stage0-home-dark.png).

Stage 0 is implemented and validated sufficiently to proceed under the owner’s conditional Stage 1 authorization. Physical-device and themed-launcher checks remain outside this emulator validation.

## Stage 1

Implemented first-run introduction, multiline capture, one Room draft with stable batch identity, DataStore introduction completion, Home resume entry, and the explicit resume/discard choice. Capture saves raw input without trimming; it shows saving, saved or failed status. Failed saves have manual Retry. Writes run in order in application scope, independently of navigation/Activity destruction. No tasks table, alarm scheduler, notification permission request or network client exists at this stage. Backup and device transfer of app data are excluded by the foundation manifest/rules.

Dependencies added: Room 3.0.3, KSP 2.3.14, Preferences DataStore 1.3.0. Setup checked against official Android CLI documentation for Room and DataStore. The v1 database schema is exported under `apps/android/app/schemas`.

### Automated checks

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`

- Debug build passes.
- Unit test task has no sources; persistence is tested against actual Room on the emulator.
- Lint passes with no errors. Warnings concern newer available build/library versions, the redundant v26 resource qualifier and the fallback adaptive-icon file lacking a monochrome entry (the API 33+ override has one). Existing foundation pins are retained.
- Four connected tests pass: Home/capture/Settings Back navigation; saved destination after Activity recreation; exact multiline input after recreation, resume and explicit discard; and a file-backed Room database close/reopen with stable identity during edits and replacement on discard.

### Manual acceptance

- Fresh installation showed the introduction offline; its hierarchy and both entry actions were observed in `android layout`. The connected tests exercise the Home entry; the running app was then used for capture.
- Preserved the draft already entered in the emulator. Its trailing newline remained exact in the EditText layout after portrait → landscape → portrait rotation.
- After the saved acknowledgement, used `adb shell am force-stop com.ctxaware.irada` and `adb shell am start -n com.ctxaware.irada/.MainActivity`. The introduction stayed dismissed. Home remained empty and showed **Resume draft**; resuming restored the exact input including its newline.
- Opened Gboard’s full on-screen keyboard using its **Show on-screen keyboard** menu. The input, save acknowledgement, toolbar Back and **Back to tasks** all remained visible above the keyboard in portrait. Captured and visually checked the dark keyboard layout.
- System Back dismissed the keyboard. A separate Back from capture with the keyboard closed returned to empty Home with **Resume draft**; navigation and relaunch preserved the draft. Explicit discard was tested with synthetic input in the connected test, without discarding the draft used during manual review.
- Airplane mode was enabled and emulator Wi-Fi disabled during Stage 1 manual checks. Rotation and hardware-keyboard settings were restored after testing. A later screenshot attempt overlapped live interaction: its tap landed on Gboard’s period key. The exact prior input plus that one period was verified, and only the accidental period was removed. No subsequent draft edits were made.

Screenshots: [foundation light](android/stage0-home-light.png), [foundation dark](android/stage0-home-dark.png), [capture light](android/stage1-capture-light.png), [capture landscape](android/stage1-capture-landscape.png), [capture with keyboard, dark](android/stage1-keyboard-dark.png).

Stage 1 is implemented and validated on the API 37 phone emulator, ready for owner review. Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

### Limits

Physical device, TalkBack, 200% text, wider layouts, predictive Back animation and simulated disk-write failures have not been exercised. Normal Back must preserve input; forced termination before the “Draft saved on this device” acknowledgement can interrupt a write. Plan generation and approval are intentionally later stages. No Stage 2 work has started.
