import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const apiDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultDatabasePath = resolve(apiDirectory, '..', 'data', 'pocketbuddy.sqlite');

export function openDatabase(databasePath = process.env.DATABASE_PATH ?? defaultDatabasePath) {
  if (databasePath !== ':memory:') {
    mkdirSync(dirname(databasePath), { recursive: true });
  }

  const database = new DatabaseSync(databasePath);
  database.exec('PRAGMA foreign_keys = ON;');
  database.exec('PRAGMA journal_mode = WAL;');
  migrate(database);
  return database;
}

function migrate(database) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS tasks (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL CHECK (length(trim(title)) > 0),
      notes TEXT,
      due_date TEXT,
      reminder_at TEXT,
      status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'completed')),
      source_input TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      completed_at TEXT
    );

    CREATE INDEX IF NOT EXISTS tasks_status_due_date_idx
      ON tasks(status, due_date);

    CREATE TABLE IF NOT EXISTS pending_draft (
      singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
      input TEXT NOT NULL,
      proposal_json TEXT,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS settings (
      singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
      default_reminder_time TEXT NOT NULL DEFAULT '09:00',
      appearance TEXT NOT NULL DEFAULT 'system' CHECK (appearance IN ('system', 'light', 'dark')),
      notifications_enabled INTEGER NOT NULL DEFAULT 0 CHECK (notifications_enabled IN (0, 1)),
      updated_at TEXT NOT NULL
    );
  `);

  database.prepare(`
    INSERT OR IGNORE INTO settings (
      singleton_id,
      default_reminder_time,
      appearance,
      notifications_enabled,
      updated_at
    ) VALUES (1, '09:00', 'system', 0, ?)
  `).run(new Date().toISOString());
}

export function toTask(row) {
  if (!row) return null;

  return {
    id: row.id,
    title: row.title,
    notes: row.notes,
    dueDate: row.due_date,
    reminderAt: row.reminder_at,
    status: row.status,
    sourceInput: row.source_input,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    completedAt: row.completed_at,
  };
}

export function toSettings(row) {
  return {
    defaultReminderTime: row.default_reminder_time,
    appearance: row.appearance,
    notificationsEnabled: Boolean(row.notifications_enabled),
    updatedAt: row.updated_at,
  };
}

export { defaultDatabasePath };
