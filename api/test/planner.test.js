import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createPlanPreview } from '../src/planner.js';
import { buildApp } from '../src/app.js';

const beforeMidnight = new Date('2026-11-20T18:59:59Z'); // Karachi 23:59:59
const context = { locale: 'en-PK', timeZone: 'Asia/Karachi', defaultReminderTime: '08:30' };

test('the three acceptance examples distinguish dates from reminders', async () => {
  const anytime = await createPlanPreview('Buy groceries', context, beforeMidnight);
  assert.equal(anytime.tasks[0].dueDate, null);
  assert.equal(anytime.tasks[0].reminderAt, null);
  const dated = await createPlanPreview('Buy groceries tomorrow', context, beforeMidnight);
  assert.equal(dated.tasks[0].dueDate, '2026-11-21');
  assert.equal(dated.tasks[0].reminderAt, null);
  const reminded = await createPlanPreview('Remind me tomorrow at 9 to buy groceries', context, beforeMidnight);
  assert.equal(reminded.tasks[0].dueDate, '2026-11-21');
  assert.equal(reminded.tasks[0].reminderAt, '2026-11-21T09:00:00');
});

test('relative dates use one request clock and the supplied zone across midnight', async () => {
  for (const [instant, expected] of [
    ['2026-11-20T18:59:59Z', '2026-11-21'],
    ['2026-11-20T19:00:00Z', '2026-11-22'],
    ['2026-12-31T23:59:59Z', '2027-01-02'],
  ]) {
    const preview = await createPlanPreview('Remind me tomorrow at 9 to buy groceries', context, new Date(instant));
    assert.equal(preview.tasks[0].dueDate, expected);
    assert.equal(preview.tasks[0].reminderAt, `${expected}T09:00:00`);
  }
  const la = await createPlanPreview('Buy groceries tomorrow', { timeZone: 'America/Los_Angeles' }, new Date('2026-11-21T01:00:00Z'));
  assert.equal(la.tasks[0].dueDate, '2026-11-21');
});

test('default time requires reminder intent, passed times are flagged, dates stay separate', async () => {
  const preview = await createPlanPreview('Remind me today to call Mum', context, beforeMidnight);
  assert.equal(preview.tasks[0].dueDate, '2026-11-20');
  assert.equal(preview.tasks[0].reminderAt, '2026-11-21T08:30:00');
  assert.ok(preview.warnings.some(warning => warning.includes('passed')));
  const plain = await createPlanPreview('Call Mum today', context, beforeMidnight);
  assert.equal(plain.tasks[0].reminderAt, null);
  for (const input of ['Remind me to call Mum', 'Remind me tomorrow at 25:99 to call Mum', 'Remind me tomorrow at 9:999 to call Mum', 'Remind me tomorrow at nine to call Mum']) {
    const invalid = await createPlanPreview(input, context, beforeMidnight);
    assert.equal(invalid.tasks[0].reminderAt, null);
    assert.ok(invalid.warnings.length);
  }
});

test('date and reminder context do not leak between tasks; unknown timing stays undated', async () => {
  const preview = await createPlanPreview('Buy groceries tomorrow, call Sara this week, renew passport', context, beforeMidnight);
  assert.equal(preview.tasks[0].dueDate, '2026-11-21');
  assert.deepEqual(preview.tasks.slice(1).map(task => [task.dueDate, task.reminderAt]), [[null, null], [null, null]]);
});

test('endpoint keeps input-only callers and rejects invalid optional context without writing', async t => {
  const app = buildApp({ databasePath: ':memory:', now: () => beforeMidnight });
  t.after(() => app.close());
  for (const payload of [{ input: 'Buy groceries' }, { input: 'Buy groceries tomorrow', ...context }]) {
    const result = await app.inject({ method: 'POST', url: '/api/plan-preview', payload });
    assert.equal(result.statusCode, 200);
    assert.equal(result.json().input, payload.input);
  }
  for (const extra of [{ timeZone: 'Mars/Unknown' }, { locale: 'not_a_locale' }, { defaultReminderTime: '24:00' }]) {
    const result = await app.inject({ method: 'POST', url: '/api/plan-preview', payload: { input: 'Buy groceries', ...extra } });
    assert.equal(result.statusCode, 400);
  }
  assert.equal((await app.inject({ method: 'POST', url: '/api/plan-preview', payload: { input: '   ' } })).statusCode, 400);
  assert.deepEqual((await app.inject('/api/tasks')).json(), { tasks: [] });
  assert.equal((await app.inject('/api/draft')).statusCode, 404);
});
