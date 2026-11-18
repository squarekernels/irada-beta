# PocketBuddy data

The API creates `pocketbuddy.sqlite` in this directory on first start or when running `npm run db:init` from `api/`.

SQLite database files and their WAL/SHM companions are intentionally ignored. The schema is created by `api/src/db.js`.
