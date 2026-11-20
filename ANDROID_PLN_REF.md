# Lumio Android app — staged implementation plan

## Context

The repo holds a UX brief (`README.md`), a read-only local API (`api/`), and an approved design system (`design/branding/`), but no `android/` project. The owner asked for the plan to build the native Android app, built **in stages (roughly one screen per stage), not all at once**, delegating to cheaper subagent models (Sonnet, Haiku) where that makes sense.

Owner decisions taken during planning (2026-11-17):

| Decision | Value |
| --- | --- |
| applicationId / namespace | `dev.master.lumio` |
| minSdk / compileSdk / targetSdk | 26 / 36 / 36 |
| Catalog | **No offline use** — online only, no bundled or persisted catalog |
| Delivery | One stage at a time; stop for owner approval after each |

"No offline use" overrides the offline rule in `AGENTS.md:11` and the brief's offline paragraph. Stage 0 records this in `AGENTS.md` so the docs and the app agree. Ratings and settings are still stored on the device (the brief requires them to survive between sessions); only catalog data needs the network. Without a connection the app shows an error state with Retry.

## Architecture

- **Stack:** Kotlin, Jetpack Compose + Material 3, single `:app` module in `android/`, Gradle wrapper + version catalog. Library versions are resolved at scaffold time from current official Android docs, not from the agent's memory.
- **Structure** (`android/app/src/main/java/dev/master/lumio/`): `data/` (API, ratings DB, settings), `domain/` (recommendation engine, trivia/puzzle logic — pure Kotlin, unit-tested), `ui/theme/`, `ui/components/`, `ui/<screen>/` (screen + ViewModel + UI state), `navigation/`.
- **State:** ViewModel + `StateFlow`; each screen has one sealed UI state covering loading / content / empty / error.
- **DI:** a small manual container on the `Application` class (no Hilt — fewer moving parts for a single module).
- **Networking:** Retrofit + OkHttp + kotlinx.serialization.
  - Explore, details, Swipe use `/movies` (paged, `offset`/`limit`, "has more" computed from `total`), `/movies/{id}`, `/genres`.
  - Genie and Trivia need the whole dataset, so `/catalog` (~10 MB) is loaded once per session into memory by a `CatalogRepository`, lazily on first use, stream-parsed. Not written to disk.
  - Base URL via `BuildConfig`: debug `http://10.0.2.2:3000` with cleartext allowed only in the debug network-security config; release `https://movie-api-delta-six.vercel.app`.
- **Local persistence:** Room for ratings only — `ratings(movieId, value LIKE|DISLIKE|UNSEEN, updatedAt)` plus a snapshot of the movie fields needed for history and scoring (title, year, posterUrl, genres, director, cast), and an `undo_log` (movieId, previousValue or null) so undo restores the previous value or removes a first rating. DataStore for appearance and game sound.
- **Images:** Coil with supplied `posterUrl` only (downsampled to view size; URLs never rewritten). Missing/failed poster → procedural fallback (colour derived from a hash of the id, title in Fraunces on a neutral panel), except Poster Puzzle: only actual successfully loaded posters are playable.
- **Data rules:** `rating 0`, `runtime 0`, `director ""`, empty `cast` mean missing → hide the field, never invent. Catalog text (titles, synopses, genres, trivia) stays in its source language; only UI strings are localized. Keep `sourceUrl` / `datasetSourceUrl` reachable from details.
- **Design system:** tokens from `design/branding/COLORS-TYPOGRAPHY.md` (Editorial light/dark, Fraunces 600 headings, DM Sans body, sp sizes), icons from `design/branding/icons/lucide/` converted to VectorDrawables (24 dp, 2-unit stroke, round caps), nav selection = Ink icon on Coral 48×32 dp radius-8 indicator, 48 dp touch targets, no Material icons. `design/screens/editorial-v1/` is the layout reference; `design/stitch/` is not followed (invented ratings, different style).
- **Navigation:** Navigation Compose with type-safe routes. `NavigationSuiteScaffold` gives a bottom bar on phones and a rail on wide screens; four destinations (Explore, Swipe, Genie, Arcade) each keep their own back stack; Settings from a top-bar gear; details, history, settings and games are full-screen with back navigation.
- **Strings:** `values/`, `values-es/`, `values-de/`, `values-pt/`, seeded from the tables in `design/branding/ICONS.md:19-54`; every stage adds keys to all four.

## Stages

Each stage ends with: build + tests + lint, a run on the `Pixel_9_Pro_XL` emulator against the local API, results appended to `validation/android.md`, then **stop for owner approval**. After approval, commit and push to `origin main` (per `AGENTS.md`), then start the next stage. `initial-state` is never touched.

| # | Stage | Delivers |
| --- | --- | --- |
| 0 | Foundation | `android/` project, wrapper, `.gitignore` additions (`*.jks`, `*.keystore`); theme, bundled fonts + OFL notices, 29 icon drawables + Lucide licence, base strings ×4; app shell with four placeholder destinations and settings gear; API client + models; interim launcher icon from `lumio-c-symbol-dark.png`; `AGENTS.md` updated with the decisions above |
| 1 | Explore | Search (title/director/cast via `q`), genre chips from `/genres`, sort menu, spotlight movie, paged poster grid, loading / empty / error states |
| 2 | Movie details | Poster, synopsis, year, genres, runtime, director, cast, source rating, "View source" link; Like / Dislike / Not seen yet buttons backed by the Room ratings store |
| 3 | Swipe | One-card queue from `/movies?sort=popularity&order=desc` minus rated ids; right / left / down gestures plus equal buttons; Undo; reduced-motion handling |
| 4 | Your choices | Preference history route: list of ratings, change or undo each |
| 5 | Genie | Scoring engine (genre ±1, director ±3, actor ±2; rated movies incl. unseen excluded), reasons naming the matching genres/directors/actors, "get started" empty state |
| 6 | Arcade hub + Trivia | Hub with two cards; trivia rounds from catalog `trivia`: 5 questions, 20 s countdown, points + streak bonus, 50:50, pause, hold-to-read reveal, results, play again. Owner confirmed 5 questions / 20 seconds on 2026-11-17 |
| 7 | Poster Puzzle | 3×3 tile swap from a random poster, progress, completion, next puzzle |
| 8 | Settings | System / Light / Dark, game sound toggle, licences and data attribution (fonts, Lucide, `data/SOURCE-ATTRIBUTION.md` sources) |
| 9 | Hardening | TalkBack labels, 200% font scale, wide-screen pass, string alignment audit ×4, lint clean, final validation record |

## Subagent delegation

The plan is written for any coding agent that can spawn subagents on cheaper models. It uses three roles; map them to whatever the executing tool offers.

| Role | Claude Code | Codex | Other tools |
| --- | --- | --- | --- |
| **Lead** | the session's main model | the session's main model | the strongest model available |
| **Mid tier** | Sonnet | Terra | a mid-priced general coding model |
| **Small tier** | Haiku | Luna | the cheapest, fastest model |

If the tool cannot spawn subagents or pick their model, the lead does the work itself in the same order; nothing else in the plan changes.

| Role | Used for |
| --- | --- |
| **Small tier** | Mechanical, checkable work: SVG → VectorDrawable conversion of the 29 icons; adding new string keys to `es` / `de` / `pt`; string-key alignment audits; licence/notice files; appending validation results |
| **Mid tier** | Bounded code with a clear spec: unit tests for the scoring engine, undo logic and trivia scoring; the stateless composables of each screen (cards, chips, state placeholders) from the design tokens; Compose previews for light/dark/large-text; an independent review of each stage's diff before asking the owner for approval |
| **Lead** | Architecture, the data layer, ViewModels, gesture and game logic, the recommendation engine, integration, review of every subagent diff, and anything a subagent returns that fails build or review |

Rules for every delegated task:

- The brief is self-contained: goal, exact files to create or edit, the spec files to read (`design/branding/*.md`, this plan), and what "done" looks like (for example "`./gradlew :app:assembleDebug` passes").
- One writer per file set; two subagents never edit the same files.
- Subagents never commit, push, or switch branches. The lead reviews, builds, and commits after owner approval.
- A subagent's report is checked against the diff and the build, not taken on trust.

Stages 0 and 9 are where delegation pays most (parallel mechanical work). In screen stages, typically one mid-tier subagent builds components and tests alongside the lead's ViewModel and data work, plus one small-tier subagent for strings.

## Known limits to carry in the docs

- 97% of movies have no director or cast, so Genie reasons will mostly be genre-based.
- The local catalog inspected for stage 6 has 1,109 questions across 247 movies; questions/answers are English only. Counts can change with the supplied catalog.
- No vector logo exists; the launcher icon is an interim raster until production artwork is chosen.
- Fonts are not in the repo; Stage 0 downloads Fraunces and DM Sans from `github.com/google/fonts` (ask the owner before downloading).
- `cmdline-tools` are missing from the SDK; if a needed SDK component is absent it must be installed from Android Studio.

## Verification (every stage)

1. `curl --fail http://127.0.0.1:3000/health`; start with `npm --prefix api start` if down.
2. `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
3. Install on the emulator (`android` CLI or `adb install`), walk the stage's flows, capture screenshots in light and dark, and check one non-English locale.
4. Stop the API and confirm the error + Retry state appears (replaces the offline checks).
5. Record actual results and limits in `validation/android.md`.

## Stage 0 implementation status — 2026-11-17

Foundation approved by the owner on 2026-11-17; committing/pushing and starting stage 1 authorized. Fonts and notices are now bundled (see `android/ASSETS.md`). SDK 36 was already installed; no SDK component installation was needed. Build pins, setup and architecture are documented in `android/README.md`; actual checks and remaining limits are recorded in `validation/android.md`. Stage 0 was committed and pushed to `origin/main` as `9ddf76d`. Stage 1 was subsequently approved by the owner.

## Stage 1 implementation status — 2026-11-17

Explore implemented locally with debounced search, API genres, six sort orders, a spotlight from the first matching film, 24-item pages with Load more, and loading/empty/error/retry states. Source links and dataset credits are available without waiting for stage 2. Live emulator testing exposed stale pooled GET connections; standard OkHttp recovery is now enabled and regression-tested. See `validation/android.md` for actual results and limits. Stage 1 was approved by the owner on 2026-11-17 for commit and push, including the physical-phone debug setup. Stage 1 was committed and pushed as `7b47f94`. The owner authorized stage 2 on 2026-11-17.

## Stage 2 implementation status — 2026-11-17

Movie details implemented locally: tapping a grid card or the spotlight opens a full-screen `MovieDetails(id)` route that loads `/movies/{id}` online, with loading, not-found, and error + Retry states. It shows the poster, year · runtime · genres, title, synopsis, and a movie-information list (release year, genres, runtime, director, cast, source rating with vote count), hiding any missing field. Source and dataset links moved from Explore cards to details. Like / Dislike / Not seen yet are saved in Room (`ratings` with the planned movie snapshot, plus a bounded `undo_log`); each save shows a snackbar with Undo, which restores the previous value or removes a first rating. Undo rules live in a pure `RatingsLedger` that is unit-tested; the Room schema is exported to `android/app/schemas/`. A two-column layout is used at 720 dp and wider. See `validation/android.md` for actual results. The owner approved stages 2 and 3 on 2026-11-17; both were committed and pushed as `9e468d4`. Stage 4 was authorized.


## Stage 3 implementation status — 2026-11-17

Swipe implemented locally after the owner said “Start with Step 3”: online popularity-descending pages, one card at a time, all saved movie ids (including UNSEEN) excluded, right/left/down gestures and equal Like/Dislike/Not seen yet buttons, persisted global Undo, and details navigation. Rating changes in details update the queue; removing a rating through Undo reloads online so a consumed movie can return. Fully-rated pages are skipped; loading, exhausted, network error + Retry, local-storage error + Retry, and failed-save feedback are distinct. Only unread page records are held in memory. System animation disablement removes drag transforms; no timed card animations are used. UI keys remain aligned across four languages. See `validation/android.md` for 40 passing unit tests, the Room device test, emulator flows and remaining limits. The owner approved stages 2 and 3 on 2026-11-17; both were committed and pushed as `9e468d4`. Stage 4 was authorized.


## Stage 4 implementation status — 2026-11-17

Your choices implemented locally: a full-screen history route from Swipe with newest-first Room snapshots, current choice, saved time, supplied poster/fallback and online details navigation. Each row opens a three-choice editor and provides Undo for that film’s latest available change. Per-film Undo is transactional and checks the displayed entry id; it restores the previous value/timestamp or removes a first rating while leaving other movies and their global undo entries intact. Undo is unavailable when that film’s entries have aged out of the existing 200-step log. History viewing/editing uses saved personal snapshots and works without catalog access; full movie details still load online. Loading, empty, local read error + Retry, failed-write and stale-undo feedback are included. All 109 string keys align across four locales. Validation: 48 passing JVM tests, 2 passing Room device tests, build/lint with zero errors; emulator walkthrough in `validation/android.md`. The owner approved stage 4 on 2026-11-17; it was committed and pushed as `d47edb4`. Stage 5 (Genie) was authorized.


## Stage 5 implementation status — 2026-11-17

Genie implemented locally: lazy process-memory `/catalog` load, pure Kotlin recommendation engine (genre ±1, director ±3, actor ±2 summed across saved like/dislike snapshots), all rated ids including UNSEEN excluded, positive net scores only, strongest 30 results. Ties use popularity, then normalized title and id. Blank features are ignored and repeated/case-varied features count once per movie. Each card shows up to three strongest positive genre/director/actor matches using actual supplied names, never an invented rating or match percentage. Ratings/history/Undo changes recompute off the main thread and cancel stale work. Empty/only-UNSEEN preferences show getting-started guidance without fetching the catalog; no positive results, network error + Retry and local-read error + Retry have distinct states. Details and history routes return to Genie. All 122 string keys align across four locales. See `validation/android.md`: 61 JVM tests pass, build/lint pass, live emulator flows verified. The owner approved stage 5 on 2026-11-17; it was committed and pushed as `864a7c5`. Stage 6 (Arcade hub and Trivia) was authorized.


## Stage 6 implementation status — 2026-11-17

Arcade hub and Trivia implemented locally. The owner selected **5 questions / 20 seconds each** instead of the original 10-question proposal. The hub contains Trivia and a clearly marked forthcoming Poster Puzzle card (stage 7). Trivia lazily uses the existing in-memory catalog, validates four-choice questions and samples five without replacement by question id. Correct answers earn 100 points plus 25 for each consecutive correct answer after the first, capped at 100 extra; wrong/timeout earns zero and resets the streak. One 50:50 per round removes two wrong options. Feedback advances after four seconds unless Keep reading holds it; Next can advance manually. Results show score, correct count and best streak, with Play again and Back to Arcade. Monotonic-clock timing, lifecycle pause, explicit Resume and an Arcade-scoped ViewModel preserve a paused round across navigation/configuration changes without persisting catalog questions. Process death starts over. Loading, network error + Retry and insufficient-valid-question states are included. All 154 resource keys align across four locales; question content remains in source English. See `validation/android.md` for 74 passing JVM tests, build/lint and emulator flows. The owner approved stage 6 on 2026-11-17; it was committed and pushed as `4a937a7`. Stage 7 (Poster Puzzle) was authorized.


## Stage 7 implementation status — 2026-11-17

Poster Puzzle implemented locally: full-screen 3×3 tap-two-tiles swap game from a random supplied poster, in-place count, swap count, selection/check feedback, show/hide reference, completion and Next puzzle. Random boards are guaranteed unsolved and all permutations are solvable by arbitrary swaps. Next avoids the immediately previous film when another eligible poster is available, resets progress/selection/reference and retains no round on disk. One size-bounded Coil painter feeds all nine consistent crop regions; image data and catalog remain memory-only. Per the owner’s clarification, missing/invalid poster URLs are excluded and failed artwork cannot be played. Retry rechecks the poster; Next skips failed movies. There is no pattern fallback. Loading, catalog error + Retry and no-film states are included. An Arcade-scoped ViewModel preserves a partial board through navigation/configuration changes in the live process; process death starts over. Movie details remain online. All puzzle UI resources align across four locales. See `validation/android.md` for actual validation results, including the poster-only correction. The owner authorized stage 8 while finishing stage 7, then approved both stages for commit and push on 2026-11-17.


## Stage 8 implementation status — 2026-11-17

Authorized by the owner while stage 7 remains pending. Settings adds System / Light / Dark appearance, game sound, bundled font/icon licences and catalog source notices/links. Preferences DataStore stores only settings on device; catalog data remains memory-only. Theme changes apply across the app, including Trivia feedback colours. Sound events cover Trivia feedback/timeouts and Puzzle swaps/completion, use media volume, and do not replay on navigation. Read/write errors have explicit feedback. See `validation/android.md` for actual checks and limitations. The owner approved stages 7 and 8 for commit and push on 2026-11-17. The owner then instructed not to do stage 9; it remains unstarted and requires new direction.

## Scoped owner refinements — 2026-11-17

After stages 7/8 approval and the instruction not to start Stage 9, the owner authorized newest-first Explore, full-poster/neutral-action Swipe, focused Genie, a Trivia redesign using the earlier design ideas, and draggable Puzzle pieces with 3×3/4×4/6×6 sizes. The owner approved these completed refinements for commit and push on November 18, 2026; see [behavior and design notes](ANDROID-REFINEMENTS.md) and [actual validation](../validation/android.md). Stage 9 and iOS remain unstarted.