import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildApp } from '../src/app.js';

test('previews a plan without saving tasks, then saves the approved batch', async (t) => {
  const app = buildApp({ databasePath: ':memory:' });
  t.after(() => app.close());

  const previewResponse = await app.inject({
    method: 'POST',
    url: '/api/plan-preview',
    payload: {
      input: 'Renew my passport, buy groceries tomorrow, and call Sara this week.',
    },
  });

  assert.equal(previewResponse.statusCode, 200);
  const preview = previewResponse.json();
  assert.equal(preview.isStub, true);
  assert.equal(preview.tasks.length, 3);

  const emptyResponse = await app.inject({ method: 'GET', url: '/api/tasks' });
  assert.deepEqual(emptyResponse.json(), { tasks: [] });

  const approvalResponse = await app.inject({
    method: 'POST',
    url: '/api/tasks/batch',
    payload: { input: preview.input, tasks: preview.tasks.map(({ suggested, reason, ...task }) => task) },
  });

  assert.equal(approvalResponse.statusCode, 201);
  assert.equal(approvalResponse.json().tasks.length, 3);
});

test('supports task completion, filtering, and deletion', async (t) => {
  const app = buildApp({ databasePath: ':memory:' });
  t.after(() => app.close());

  const approvalResponse = await app.inject({
    method: 'POST',
    url: '/api/tasks/batch',
    payload: {
      input: 'Call Sara',
      tasks: [{ title: 'Call Sara', notes: null, dueDate: null, reminderAt: null }],
    },
  });
  const task = approvalResponse.json().tasks[0];

  const completionResponse = await app.inject({
    method: 'PATCH',
    url: `/api/tasks/${task.id}`,
    payload: { status: 'completed' },
  });
  assert.equal(completionResponse.statusCode, 200);
  assert.equal(completionResponse.json().status, 'completed');
  assert.ok(completionResponse.json().completedAt);

  const completedResponse = await app.inject({
    method: 'GET',
    url: '/api/tasks?status=completed',
  });
  assert.equal(completedResponse.json().tasks.length, 1);

  const deletionResponse = await app.inject({ method: 'DELETE', url: `/api/tasks/${task.id}` });
  assert.equal(deletionResponse.statusCode, 204);
});

test('persists a single draft and updates settings', async (t) => {
  const app = buildApp({ databasePath: ':memory:' });
  t.after(() => app.close());

  const draftResponse = await app.inject({
    method: 'PUT',
    url: '/api/draft',
    payload: { input: 'Something I need to remember' },
  });
  assert.equal(draftResponse.statusCode, 200);

  const savedDraftResponse = await app.inject({ method: 'GET', url: '/api/draft' });
  assert.equal(savedDraftResponse.json().input, 'Something I need to remember');

  const settingsResponse = await app.inject({
    method: 'PATCH',
    url: '/api/settings',
    payload: { appearance: 'dark', defaultReminderTime: '08:30' },
  });
  assert.equal(settingsResponse.json().appearance, 'dark');
  assert.equal(settingsResponse.json().defaultReminderTime, '08:30');
});
