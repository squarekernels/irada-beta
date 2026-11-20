// Local validation fixture, never used by src/server.js. All data is in memory.
import { buildApp } from '../src/app.js';

const app = buildApp({ databasePath: ':memory:' });
let mode = 'normal';
let requests = 0;
app.addHook('preHandler', async (request, reply) => {
  if (request.url !== '/api/plan-preview') return;
  requests++;
  if (mode === 'slow') await new Promise(resolve => setTimeout(resolve, 30_000));
  if (mode === 'failure') return reply.code(503).send({ error: 'Validation failure' });
  if (mode === 'invalid') return reply.send({ invalid: true });
});
app.post('/__validation', async (request, reply) => {
  if (!['normal', 'slow', 'failure', 'invalid'].includes(request.body?.mode)) return reply.code(400).send();
  mode = request.body.mode;
  requests = 0;
  return { mode, requests };
});
app.get('/__validation', async () => ({ mode, requests }));
await app.listen({ host: '127.0.0.1', port: Number(process.env.PORT ?? 3000) });
console.log('Preview validation fixture listening on loopback only. No inputs are logged.');
