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

## Stage 2 — November 21, 2026

Implemented on `codex/android-stage-2`, based on approved Stage 1 commit `af88a2c`. No commits or pushes have been made for this stage. Stage 3 has not started.

### Delivered behavior

- Capture submits only nonblank input up to 10,000 characters, after a fresh Room acknowledgement. A failed local save prevents sending; Retry is explicit.
- Processing has Cancel and Back. Both cancel the OkHttp call and return to the original input. Request generations ignore late results, including a non-cooperative service responding after a newer request.
- Read-only preview shows original input, proposal titles, Suggested labels, Anytime, separate due dates/reminders and interpretation warnings. It clearly identifies the placeholder planner. There is no approval action, task insertion or scheduling.
- Rotation retains processing/result without another request. After process death the Room input resumes from Home; it is never submitted automatically. Preview results stay in memory until the later editable-draft stage.
- Request context includes the current locale, current IANA time zone and default `09:00` reminder time. Stage 7 will add the configurable preference. Fastify retains input-only callers, uses one clock reading per request, and returns warnings for unsupported reminder timing or passed times.
- Networking uses OkHttp 4.12.0 and kotlinx.serialization 1.8.1. Connect/read/total timeouts are 5/15/20 seconds. Redirects and connection retries are disabled. Response parsing checks matching original input, task count, bounded text, valid calendar dates and wall-clock times; body size is limited to 1 MB.
- Debug HTTP is allowed only for loopback. `adb reverse tcp:3000 tcp:3000` connects to the local server. Release builds require a configured HTTPS URL and have no default preview endpoint. Android namespace stays fixed so an isolated validation application ID can be used.

### Automated results

| Check | Result |
| --- | --- |
| `npm test --prefix api` | 8 tests passed |
| `:app:testDebugUnitTest` | 10 tests passed |
| `:app:connectedDebugAndroidTest` with `-Pirada.applicationId=com.ctxaware.irada.validation` | 5 tests passed before opt-in HTTP tests were added |
| Same connected task with `-Pandroid.testInstrumentationRunnerArguments.runLivePreviewTests=true` | All 7 tests passed, including live Fastify HTTP |
| `:app:assembleDebug :app:lintDebug` | Passed; lint has no errors |

API coverage includes the three required grocery examples, both sides of local midnight in Asia/Karachi, a negative-offset zone, year rollover, reminder defaults/intent, invalid times, passed-time warnings, clause isolation, optional-context validation and input-only compatibility. Preview leaves the server task list empty.

Android unit coverage includes durable-save ordering, duplicate taps, cancellation during save, save failure, manual retry, refreshed time-zone context, non-cooperative stale responses, real HTTP request context, malformed/mismatched/date-invalid output, HTTP failure without retry, slow-call cancellation and timeout.

Connected coverage includes all Stage 1 tests plus processing rotation, cancellation, failure/manual recovery, read-only preview rotation, exact input after editing/back and empty Home. The opt-in HTTP tests exercise actual Fastify output for all three examples, malformed output, 503 and explicit retry. Tests use a separate application ID so install/uninstall never clears the original app’s draft.

Final review build passed `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:generateReleaseBuildConfig :app:processReleaseMainManifest` with the normal application ID. Generated release configuration has an empty preview endpoint; debug has the forwarded loopback endpoint. Installed the normal review APK and confirmed Home with Resume draft, without opening or changing that existing draft. Existing lint warnings remain for newer available dependencies, a redundant v26 qualifier and the fallback adaptive-icon file; no lint baseline or suppression was added.

### Manual emulator acceptance

On `Medium_Phone_API_37.0` / Android 17, using only `com.ctxaware.irada.validation` data:

1. Entered “Remind me tomorrow at 9 to buy groceries”; Room acknowledged the save. The live preview showed November 22, 2026 as due date and a separate proposed 9 AM reminder, appropriate to the emulator’s November 21 local date.
2. Used an isolated in-memory fixture on host port 3001 because an existing owner-run Fastify server occupied port 3000. Temporarily forwarded device port 3000 to host 3001. A pre-existing pooled connection remained attached to the original server after forwarding changed; restarted only the validation app to clear that connection before the stopped-server test.
3. Stopped only the isolated fixture. Submission failed with Retry/Edit input. Restarted the fixture: its request count remained zero and the app stayed on the error screen until Retry was tapped. Retry succeeded.
4. Set the fixture to a 30-second delay. Processing was visible and Cancel returned immediately to the exact saved input. A subsequent normal request succeeded; the canceled response did not replace the current state.
5. Enabled airplane mode and disabled Wi-Fi. Submission showed the offline error. After restoring connectivity, the error remained, and the fixture request count did not increase until Retry. This also left time for the earlier canceled server request to finish without replacing the error.
6. Switched light/dark appearance and visually inspected the proposal. Captured the settled dark state after the system-bar theme transition.
7. Force-stopped/reopened the validation app from a preview. Home offered Resume draft; the exact original input returned without a network request.
8. Restored light appearance, active connectivity, and forwarding to the owner’s original port 3000 server. The normal installed app’s input was never edited or cleared during Stage 2 validation.

Screenshots: [capture](android/stage2/capture-light.png), [processing](android/stage2/processing-light.png), [connection error](android/stage2/error-light.png), [preview light](android/stage2/preview-light.png), [preview dark](android/stage2/preview-dark.png).

### Scope and limitations

The stub recognizes a small English vocabulary (`today`, `tomorrow`, numeric reminder times) and deliberately leaves unrecognized timing undated. It is not AI; general extraction, arbitrary dates and DST-gap disambiguation are unimplemented. No production HTTPS service is configured. Release configuration was checked, but a production endpoint/release-device journey was not tested. Disk-write failure is covered at the controller boundary, not by exhausting real device storage. TalkBack, 200% text, other API levels and physical devices remain unverified. Preview persistence/editing belongs to Stage 3; task approval belongs to Stage 4.
