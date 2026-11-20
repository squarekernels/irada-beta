# PocketBuddy API

A minimal Fastify API backed by SQLite. The planner endpoint is intentionally stubbed and does not call an AI service.

## Run locally

Requires Node.js 22.13 or newer.

```sh
npm install
npm run db:init
npm run dev
```

The server listens on `http://localhost:3000` by default. Override `PORT`, `HOST`, or `DATABASE_PATH` with environment variables when needed.

## API surface

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Liveness check |
| `POST` | `/api/plan-preview` | Produce a placeholder plan without saving it |
| `GET` | `/api/draft` | Read the single pending draft |
| `PUT` | `/api/draft` | Create or replace the pending draft |
| `DELETE` | `/api/draft` | Discard the pending draft |
| `POST` | `/api/tasks/batch` | Save an explicitly approved task batch |
| `GET` | `/api/tasks` | List tasks; supports `status` and `search` |
| `GET` | `/api/tasks/:id` | Read one task |
| `PATCH` | `/api/tasks/:id` | Edit, complete, uncomplete, or reschedule a task |
| `DELETE` | `/api/tasks/:id` | Delete one task |
| `GET` | `/api/settings` | Read local preferences |
| `PATCH` | `/api/settings` | Update local preferences |
| `DELETE` | `/api/data` | Delete tasks/draft and reset settings |

### Placeholder planner

`POST /api/plan-preview` returns `isStub: true`. Its implementation lives in `src/planner.js`, giving a future AI integration one narrow seam to replace without changing the UI-facing response shape.

### Example flow

```sh
curl -X POST http://localhost:3000/api/plan-preview \
  -H 'content-type: application/json' \
  -d '{"input":"Renew my passport, buy groceries tomorrow, and call Sara this week."}'
```

After the user edits and approves that response, send the accepted task fields to `POST /api/tasks/batch`.

Due dates use `YYYY-MM-DD`. Reminder values use local wall-clock ISO strings such as `2026-11-19T09:00:00` without a UTC offset, matching the product rule that ordinary reminders remain anchored to the intended local time when the user travels.

## Preview context (Android Stage 2)

`POST /api/plan-preview` accepts `input` (1–10,000 characters) and optional:

- `locale`: a BCP 47 language tag, such as `en-PK`.
- `timeZone`: an IANA time zone, such as `Asia/Karachi`. Input-only callers use UTC.
- `defaultReminderTime`: local `HH:mm`, default `09:00`; used only with explicit reminder intent.

Example body:

```json
{"input":"Remind me tomorrow at 9 to buy groceries","locale":"en-PK","timeZone":"Asia/Karachi","defaultReminderTime":"09:00"}
```

The response retains `input`, `tasks`, `isStub`, and `message`, and adds `warnings` (an array of plain-language interpretation notes). Dates are `YYYY-MM-DD`; reminders are local wall-clock `YYYY-MM-DDTHH:mm:ss` values without UTC offsets. The clock is captured once per request and relative dates use the supplied zone. Preview does not write tasks or drafts.

This is a limited English development stub, not AI. It splits newline/comma/semicolon/“and” clauses, recognizes `today` and `tomorrow`, and recognizes numeric reminder times following `at` (24-hour or AM/PM). Bare `9` means 09:00. Unrecognized timing stays undated. No reminder is proposed without “remind me” and a recognized date. A passed reminder moves to the next local day and is flagged, independently of the task’s due date. General natural-language extraction, calendar dates, and DST-gap disambiguation remain outside this stub.

| Input | Due date | Reminder |
| --- | --- | --- |
| Buy groceries | null (Anytime) | null |
| Buy groceries tomorrow | Tomorrow in supplied zone | null |
| Remind me tomorrow at 9 to buy groceries | Tomorrow in supplied zone | Tomorrow at 09:00 |

For repeatable emulator failure checks, run `node test-support/preview-server.js`. This loopback-only fixture uses an in-memory database and does not log inputs. `POST /__validation` with `{"mode":"normal"}`, `slow` (30 seconds), `failure` (503), or `invalid` controls previews. `GET /__validation` returns the mode and request count. These routes exist only in the fixture; `src/server.js` does not expose them.
