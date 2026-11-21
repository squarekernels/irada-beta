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

## Stage 3 — November 21, 2026

Stage 2 was approved, committed with the required co-author trailer, and pushed to `origin/codex/android-stage-2` as `e5b171b`. Stage 3 is implemented on `codex/android-stage-3`; it has not been committed or pushed. Stage 4 has not started.

### Delivered behavior

Review now edits the pending batch: title, multiline notes, optional due date, and an independent optional reminder date/time. Every proposal has a stable ID. Add, Remove with brief Undo, Move up/down, field edits and list order persist in the one Room draft. Undo is scoped to the current review visit, so its snackbar cannot reappear over controls after opening an editor. All ordinary navigation preserves the draft. Explicitly editing the original input asks **Keep editing** or **Discard proposals**; discarding proposals keeps the original words and batch identity.

Untouched generated fields read **Suggested**; edited or newly added fields read **Your choice**. Clearing a due date produces **Anytime**. Changing a due date does not change the reminder. Original interpretation notes remain labeled as original, and batch warnings remain visible after edits. Incomplete/overlong pending fields are flagged without discarding the user's text. Editor Done/Back save edits and return to review; review Back returns to empty Home. Resume opens the persisted review offline without generating again.

Room schema 2 adds nullable `reviewJson` with `AutoMigration(1, 2)`. Generated migration SQL is only `ALTER TABLE pending_draft ADD COLUMN reviewJson TEXT DEFAULT NULL`. Schema 1 is retained; no destructive fallback is configured. Review JSON stores proposal IDs/order, fields, edit provenance, original reasons and warnings. Approved-task storage, approval actions, alarms, and notification permission requests remain absent.

### Automated checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest -Pirada.applicationId=com.ctxaware.irada.validation -Pandroid.testInstrumentationRunnerArguments.runLivePreviewTests=true`: passed all 10 connected tests, all 14 unit tests and lint with no errors. The fixture ran on host port 3002, forwarded from device port 3000.
- Then expanded the review UI journey with actual reminder date/time selection; reran `:app:connectedDebugAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.class=com.ctxaware.irada.ReviewFlowTest`: passed. This confirms the native date picker and reminder time picker set the intended local values.
- Review journey changes a due date, edits title/notes, removes/undoes/removes a proposal, adds a new one, changes order, leaves for Home, resumes, recreates the Activity, cancels destructive input editing and resumes in a new Activity/ViewModel. Original input, exact whitespace/newlines, values, warnings and order are checked.
- File-backed Room close/reopen preserves review JSON and stable batch identity. A raw schema-1 database containing exact Unicode/whitespace input migrates to schema 2 without loss. Storage inspection confirms one `pending_draft` row and only `pending_draft`, `room_master_table`, and `android_metadata` tables.
- Unit tests verify per-field provenance, independent date/reminder values, stable IDs and ordering, remove/restore, boundary moves, empty/incomplete pending edits, codec round trips and duplicate-ID rejection. The stale-response regression also confirms canceled output never reaches the persistence callback.
- Existing capture, navigation, cancellation, network retry and all three live Fastify acceptance examples still pass with the editable review flow.

The calendar initially failed a test selector: Material 3 exposes calendar days as full-date text rather than short day-number text. The final test uses that actual label. A second failure exposed the removal snackbar surviving an editor visit and blocking a reorder tap; scoping Undo to review fixed the actual interaction. The emulator was restarted after the work resumed; the initial no-device run was rerun successfully once it was ready. No checks were bypassed or suppressed.

### Manual acceptance and evidence

Used only `com.ctxaware.irada.validation` for interaction and data inspection; the normal app's existing draft was not edited or cleared.

1. Generated a real Fastify review from “Buy groceries tomorrow and Remind me today at 1 to call Mum and Book dentist”. Verified original words, Suggested labels, separate due/reminder values and the warning for a passed reminder.
2. Changed the first proposal's title to **Buy milk and bread** and due date to **November 25, 2026**. Both labels changed to **Your choice**; the date-only proposal still had no reminder. The editor's save status and Done action stayed visible above the full software keyboard.
3. Reordered that proposal, returned to Home and force-stopped the app. Enabled airplane mode and disabled Wi-Fi before reopening. Resume restored the same edited review and order without requesting the API.
4. Removed **Book dentist**, added **Call dentist**, and force-stopped/reopened again offline. The removed item stayed removed, the added item stayed **Anytime**, and the edited title/date/order survived.
5. Copied only the synthetic validation database and WAL into `/private/tmp` for SQLite inspection. Confirmed schema 2, one draft row, all three proposals in the expected order, unchanged original input, preserved warnings and TITLE/DUE_DATE provenance. There is no approved-task table.
6. `adb shell dumpsys alarm` contained no `com.ctxaware.irada.validation` entry, including while the draft held a proposed reminder. No scheduling or notification permission request occurred.
7. Visually inspected light review, editing with the keyboard, and the restored dark review. Restored light appearance and active connectivity after testing.

Evidence: [suggestions and warning](android/stage3/review-suggestions-light.png), [editing above keyboard](android/stage3/editor-keyboard-light.png), [restored edits and Anytime, dark](android/stage3/review-restored-dark.png), [storage verification](android/stage3/storage-verification.json).

## Stage 4 — November 21, 2026

### Delivered behavior

Review approval now saves the complete reviewed batch in the Room `3` schema. The pending draft, task rows and approval receipt are committed together, while reminder values remain local intent only and are not scheduled.

The approval path waits for the latest draft save, blocks review mutations while the transaction runs, compares the full saved draft identity and contents against the screen snapshot, and rejects stale or invalid batches. Stable task identities use the batch/proposal pair. Persisted receipts make repeated approval idempotent across separate Room connections.

A SQLite insert failure rolls back task inserts, the approval receipt and draft consumption. The explicit approval retry succeeds afterward without regenerating or replacing the reviewed input. Success clears the consumed draft and returns to Home.

Home observes Room directly and refreshes the local date on resume and across midnight. Open tasks are grouped into Overdue, Today, Upcoming and Anytime, sorted by due date and stable creation order.

### Automated checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: passed after serializing Gradle daemon state.
- `./gradlew :app:connectedDebugAndroidTest -Pirada.applicationId=com.ctxaware.irada.validation -Pandroid.testInstrumentationRunnerArguments.class=com.ctxaware.irada.ApprovalFlowTest`: passed 2/2 UI tests on API 37 after clearing stale validation-app state.
- `./gradlew :app:connectedDebugAndroidTest -Pirada.applicationId=com.ctxaware.irada.validation -Pandroid.testInstrumentationRunnerArguments.class=com.ctxaware.irada.ApprovalTransactionTest`: passed 4/4 device tests.
- Unit tests and Android Lint passed with no errors.
- Tests cover duplicate approval from separate database connections, reopening, stale-review rejection, invalid approval, rollback after a second task insert fails, exact title/notes/due/reminder values, stable order and source input.

### Evidence and remaining limits

Evidence: [approval light](android/stage4/approval-light.png), [offline Home light](android/stage4/home-offline-light.png), [offline Home dark](android/stage4/home-offline-dark.png).

Remaining before owner approval: run the manual force-stop/offline walkthrough with Fastify stopped, verify exactly one copy after reopening, inspect the copied SQLite data to confirm the consumed draft, and reconfirm the rollback/retry flow with a fresh validation database. The emulator is API 37 and the implementation does not add notification permission or alarm scheduling; those remain Stage 6.

### Remaining limits

Real AI, approval and task insertion are outside Stage 3. The default reminder preference is still 09:00 until Stage 7. Tests ran on the API 37 phone emulator; physical devices, TalkBack, 200% text and wider layouts have not been validated for the new editor. An abrupt kill before the save acknowledgement can interrupt the latest queued write. The brief Undo itself is transient and is not restored after leaving review or process death. Corrupt persisted review data fails visibly and is not silently discarded.

Final normal-ID review APK: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed with **14 unit tests** and **zero lint errors**. Installed `com.ctxaware.irada` and observed Home with its existing Resume draft entry after migration. The existing draft was not opened or changed. The temporary Stage 3 fixture was stopped, and standard `adb reverse tcp:3000 tcp:3000`, light appearance and connectivity were restored. APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

## Stage 5 — November 21, 2026

Implemented on `codex/android-stage-5`. The owner approved Stage 5 for commit and push on November 21, 2026. Stage 6 has not started.

### Delivered behavior

Home searches titles and notes locally, filters Open / Completed / All, and collapses completed tasks under All. Completed shows matching tasks directly. Existing due-date groups and date/creation-order sorting remain. Task bodies open details, while separately labeled buttons complete or reopen tasks. Details edits title, notes, due date/Anytime and independent reminder intent. Save changes persists explicitly; Back asks before discarding unsaved edits, and rotation retains form values. Completion, reopening, edits and deletion offer brief Undo.

Task changes and Undo compare complete expected snapshots inside Room transactions. Stale edits and stale Undo cannot replace later changes. Delete/Undo restores the exact task identity, status, dates, notes, source input and creation order. Approval receipts retain the ordering high-water mark after deletion, preventing a newly approved batch from breaking a pending Undo. SQLite write failures preserve saved data and leave unsaved editor values available for a manual retry. Schema remains version 3. The global snackbar reserves its measured height so feedback cannot cover task actions during a quick complete → details → delete sequence.

### Automated checks

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: passed for the final normal application ID, with **20 passing JVM tests** and **zero Lint errors**.
- `./gradlew :app:connectedDebugAndroidTest -Pirada.applicationId=com.ctxaware.irada.validation`: passed on `Medium_Phone_API_37.0` in airplane mode. XML report: **23 cases, 21 passed, 2 skipped, zero failures/errors**. The skipped cases are the opt-in live Fastify HTTP tests; no live service was required for task management.
- Six Stage 5 device cases cover overdue → Today rescheduling without changing reminder intent, completion, Completed filtering, note search, delete/Undo, reopening, saved fields after Activity relaunch, unsaved Back/rotation, SQLite failure/retry, full database reopen, stale write/Undo rejection, and approval after deleting the newest task.
- A final targeted editor/rotation test also passed against the packaged validation build. Its opt-in `keepStage5Demo=true` instrumentation argument retained only its synthetic fixture for the manual checks below.
- `git diff --check`: passed.

Builds used the installed JDK 21. The pre-existing, untracked `gradle/gradle-daemon-jvm.properties` requested JDK 25 and stalled daemon startup; it was temporarily set to 21 during validation and restored byte-for-byte afterward. Existing untracked Stage 4 screenshots were preserved.

### Manual offline acceptance and evidence

The validation app retained a synthetic grocery task with notes, an overdue date and a future reminder intent. Airplane mode stayed enabled throughout. Visually inspected [Home in light appearance](android/stage5/home-offline-light.png), [task details](android/stage5/task-detail-light.png), [Home at 200% text in dark appearance](android/stage5/home-dark-200pct.png), and [Completed after force-stop/relaunch](android/stage5/completed-offline-dark.png).

At 200% text, the task body and **Complete task** remained distinct focusable actions. Completing from Home left the app on Home; Completed displayed **Reopen task**. Tapping the task body opened details with status still Completed. Force-stopping and reopening offline retained completion, notes, due date and reminder intent; All initially collapsed the completed task. Returning to Completed exposed it directly. Text size was restored to 1.0 and the original dark appearance/airplane setting were preserved. The final normal-ID APK was installed without clearing data; the separate validation app remains available with synthetic review data.

### Remaining limits

TalkBack narration/focus order, physical devices, other Android versions, landscape and wide-screen task-management layouts remain unverified. Unsaved detail edits survive rotation but require **Save changes** for durable storage. Undo is a transient, single-operation action and does not survive process death. Notification permission, alarms, and notification reconciliation remain Stage 6 work; reminder delivery is explicitly shown as unavailable.

Review APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (`com.ctxaware.irada`).
