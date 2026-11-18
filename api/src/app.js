import { randomUUID } from 'node:crypto';
import Fastify from 'fastify';
import { openDatabase, toSettings, toTask } from './db.js';
import { createPlanPreview } from './planner.js';

const taskInputSchema = {
  type: 'object',
  required: ['title'],
  additionalProperties: false,
  properties: {
    title: { type: 'string', minLength: 1, maxLength: 200 },
    notes: { type: ['string', 'null'], maxLength: 5000 },
    dueDate: { type: ['string', 'null'], format: 'date' },
    // A reminder is intentionally a local wall-clock value. Keeping it free of
    // a UTC offset prevents travel from shifting a 9:00 AM reminder.
    reminderAt: {
      type: ['string', 'null'],
      pattern: '^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$',
    },
    status: { type: 'string', enum: ['open', 'completed'] },
  },
};

export function buildApp(options = {}) {
  const app = Fastify({ logger: options.logger ?? false });
  const database = options.database ?? openDatabase(options.databasePath);

  app.addHook('onClose', async () => {
    database.close();
  });

  app.get('/health', async () => ({ status: 'ok' }));

  app.post('/api/plan-preview', {
    schema: {
      body: {
        type: 'object',
        required: ['input'],
        additionalProperties: false,
        properties: {
          input: { type: 'string', minLength: 1, maxLength: 10000 },
        },
      },
    },
  }, async (request) => createPlanPreview(request.body.input));

  app.get('/api/draft', async (_request, reply) => {
    const row = database.prepare('SELECT * FROM pending_draft WHERE singleton_id = 1').get();
    if (!row) return reply.code(404).send({ error: 'No pending draft' });
    return draftFromRow(row);
  });

  app.put('/api/draft', {
    schema: {
      body: {
        type: 'object',
        required: ['input'],
        additionalProperties: false,
        properties: {
          input: { type: 'string', minLength: 1, maxLength: 10000 },
          proposal: { type: ['object', 'null'], additionalProperties: true },
        },
      },
    },
  }, async (request) => {
    const updatedAt = new Date().toISOString();
    database.prepare(`
      INSERT INTO pending_draft (singleton_id, input, proposal_json, updated_at)
      VALUES (1, ?, ?, ?)
      ON CONFLICT(singleton_id) DO UPDATE SET
        input = excluded.input,
        proposal_json = excluded.proposal_json,
        updated_at = excluded.updated_at
    `).run(
      request.body.input,
      request.body.proposal ? JSON.stringify(request.body.proposal) : null,
      updatedAt,
    );
    return { input: request.body.input, proposal: request.body.proposal ?? null, updatedAt };
  });

  app.delete('/api/draft', async (_request, reply) => {
    database.prepare('DELETE FROM pending_draft WHERE singleton_id = 1').run();
    return reply.code(204).send();
  });

  app.post('/api/tasks/batch', {
    schema: {
      body: {
        type: 'object',
        required: ['input', 'tasks'],
        additionalProperties: false,
        properties: {
          input: { type: 'string', minLength: 1, maxLength: 10000 },
          tasks: { type: 'array', minItems: 1, maxItems: 100, items: taskInputSchema },
        },
      },
    },
  }, async (request, reply) => {
    const created = [];
    const insert = database.prepare(`
      INSERT INTO tasks (
        id, title, notes, due_date, reminder_at, status, source_input,
        created_at, updated_at, completed_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    database.exec('BEGIN');
    try {
      for (const task of request.body.tasks) {
        const now = new Date().toISOString();
        const id = randomUUID();
        const status = task.status ?? 'open';
        insert.run(
          id,
          task.title.trim(),
          task.notes ?? null,
          task.dueDate ?? null,
          task.reminderAt ?? null,
          status,
          request.body.input,
          now,
          now,
          status === 'completed' ? now : null,
        );
        created.push(toTask(database.prepare('SELECT * FROM tasks WHERE id = ?').get(id)));
      }
      database.prepare('DELETE FROM pending_draft WHERE singleton_id = 1').run();
      database.exec('COMMIT');
    } catch (error) {
      database.exec('ROLLBACK');
      throw error;
    }

    return reply.code(201).send({ tasks: created });
  });

  app.get('/api/tasks', {
    schema: {
      querystring: {
        type: 'object',
        additionalProperties: false,
        properties: {
          status: { type: 'string', enum: ['open', 'completed', 'all'], default: 'open' },
          search: { type: 'string', maxLength: 200 },
        },
      },
    },
  }, async (request) => {
    const clauses = [];
    const values = [];
    if (request.query.status !== 'all') {
      clauses.push('status = ?');
      values.push(request.query.status);
    }
    if (request.query.search) {
      clauses.push('(title LIKE ? OR notes LIKE ?)');
      const term = `%${request.query.search}%`;
      values.push(term, term);
    }
    const where = clauses.length ? `WHERE ${clauses.join(' AND ')}` : '';
    const rows = database.prepare(`
      SELECT * FROM tasks
      ${where}
      ORDER BY
        CASE WHEN due_date IS NULL THEN 1 ELSE 0 END,
        due_date ASC,
        created_at ASC
    `).all(...values);
    return { tasks: rows.map(toTask) };
  });

  app.get('/api/tasks/:id', async (request, reply) => {
    const task = toTask(database.prepare('SELECT * FROM tasks WHERE id = ?').get(request.params.id));
    if (!task) return reply.code(404).send({ error: 'Task not found' });
    return task;
  });

  app.patch('/api/tasks/:id', {
    schema: {
      body: {
        ...taskInputSchema,
        required: [],
        minProperties: 1,
      },
    },
  }, async (request, reply) => {
    const existing = database.prepare('SELECT * FROM tasks WHERE id = ?').get(request.params.id);
    if (!existing) return reply.code(404).send({ error: 'Task not found' });

    const next = {
      title: request.body.title?.trim() ?? existing.title,
      notes: Object.hasOwn(request.body, 'notes') ? request.body.notes : existing.notes,
      dueDate: Object.hasOwn(request.body, 'dueDate') ? request.body.dueDate : existing.due_date,
      reminderAt: Object.hasOwn(request.body, 'reminderAt') ? request.body.reminderAt : existing.reminder_at,
      status: request.body.status ?? existing.status,
    };
    const updatedAt = new Date().toISOString();
    const completedAt = next.status === 'completed'
      ? existing.completed_at ?? updatedAt
      : null;

    database.prepare(`
      UPDATE tasks SET
        title = ?, notes = ?, due_date = ?, reminder_at = ?, status = ?,
        updated_at = ?, completed_at = ?
      WHERE id = ?
    `).run(
      next.title,
      next.notes,
      next.dueDate,
      next.reminderAt,
      next.status,
      updatedAt,
      completedAt,
      request.params.id,
    );

    return toTask(database.prepare('SELECT * FROM tasks WHERE id = ?').get(request.params.id));
  });

  app.delete('/api/tasks/:id', async (request, reply) => {
    const result = database.prepare('DELETE FROM tasks WHERE id = ?').run(request.params.id);
    if (result.changes === 0) return reply.code(404).send({ error: 'Task not found' });
    return reply.code(204).send();
  });

  app.get('/api/settings', async () => {
    return toSettings(database.prepare('SELECT * FROM settings WHERE singleton_id = 1').get());
  });

  app.patch('/api/settings', {
    schema: {
      body: {
        type: 'object',
        minProperties: 1,
        additionalProperties: false,
        properties: {
          defaultReminderTime: { type: 'string', pattern: '^([01]\\d|2[0-3]):[0-5]\\d$' },
          appearance: { type: 'string', enum: ['system', 'light', 'dark'] },
          notificationsEnabled: { type: 'boolean' },
        },
      },
    },
  }, async (request) => {
    const current = database.prepare('SELECT * FROM settings WHERE singleton_id = 1').get();
    const updatedAt = new Date().toISOString();
    database.prepare(`
      UPDATE settings SET
        default_reminder_time = ?, appearance = ?, notifications_enabled = ?, updated_at = ?
      WHERE singleton_id = 1
    `).run(
      request.body.defaultReminderTime ?? current.default_reminder_time,
      request.body.appearance ?? current.appearance,
      Object.hasOwn(request.body, 'notificationsEnabled')
        ? Number(request.body.notificationsEnabled)
        : current.notifications_enabled,
      updatedAt,
    );
    return toSettings(database.prepare('SELECT * FROM settings WHERE singleton_id = 1').get());
  });

  app.delete('/api/data', async (_request, reply) => {
    database.exec('BEGIN');
    try {
      database.exec('DELETE FROM tasks; DELETE FROM pending_draft;');
      database.prepare(`
        UPDATE settings SET
          default_reminder_time = '09:00',
          appearance = 'system',
          notifications_enabled = 0,
          updated_at = ?
        WHERE singleton_id = 1
      `).run(new Date().toISOString());
      database.exec('COMMIT');
    } catch (error) {
      database.exec('ROLLBACK');
      throw error;
    }
    return reply.code(204).send();
  });

  return app;
}

function draftFromRow(row) {
  return {
    input: row.input,
    proposal: row.proposal_json ? JSON.parse(row.proposal_json) : null,
    updatedAt: row.updated_at,
  };
}
