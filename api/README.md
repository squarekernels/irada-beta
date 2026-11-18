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
