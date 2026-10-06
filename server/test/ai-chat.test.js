import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

process.env.MALO_MOCK_PROVIDERS = '1';
process.env.MALO_CLIENT_KEY = 'test-client-key';
process.env.DEEPSEEK_API_KEY = 'test-deepseek-key';
process.env.MALO_DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'malo-ai-'));

const { initStore, db } = await import('../src/store.js');
const { config } = await import('../src/config.js');
const { createApp } = await import('../src/app.js');

initStore();
config.ai.maxInputLength = 500;
config.ai.maxHistoryBlocks = 12;
config.ai.maxOutputTokens = 200;

let server;
let baseUrl;
const realFetch = globalThis.fetch;
let providerRequests = [];

test.before(async () => {
  globalThis.fetch = async (input, init) => {
    if (String(input).startsWith(config.ai.deepseekApiBase)) {
      providerRequests.push({ url: String(input), init });
      return new Response(JSON.stringify({
        choices: [{ message: { role: 'assistant', content: 'A short reply.' } }],
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return realFetch(input, init);
  };

  server = createApp().listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  globalThis.fetch = realFetch;
  server?.close();
  server?.closeAllConnections?.();
  fs.rmSync(process.env.MALO_DATA_DIR, { recursive: true, force: true });
});

const request = (body) =>
  fetch(`${baseUrl}/api/malo/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-MalO-Client-Key': process.env.MALO_CLIENT_KEY,
    },
    body: JSON.stringify(body),
  });

test('chat proxy restricts history and output and requires an active Pro subscription', async () => {
  const userId = 'malo-ai-test-user';
  db.saveSubscription({ userId, status: 'active', currentPeriodEnd: Date.now() + 60_000 });
  const history = Array.from({ length: 13 }, (_, index) => ({
    role: 'assistant',
    content: `history-${index}`,
  }));

  const response = await request({
    userId,
    messages: [
      { role: 'system', content: 'MalO system prompt' },
      ...history,
      { role: 'user', content: 'Hello' },
    ],
  });
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.reply, 'A short reply.');
  assert.equal(providerRequests.length, 1);
  assert.equal(providerRequests[0].url, `${config.ai.deepseekApiBase}/chat/completions`);
  assert.equal(providerRequests[0].init.headers.Authorization, 'Bearer test-deepseek-key');
  const providerBody = JSON.parse(providerRequests[0].init.body);
  assert.equal(providerBody.model, 'deepseek-chat');
  assert.equal(providerBody.max_tokens, 200);
  assert.equal(providerBody.messages.length, 14);
  assert.equal(providerBody.messages[1].content, 'history-1');
  assert.equal(providerBody.messages.at(-1).content, 'Hello');

  const inactive = await request({
    userId: 'malo-ai-inactive-user',
    messages: [
      { role: 'system', content: 'MalO system prompt' },
      { role: 'user', content: 'Hello' },
    ],
  });
  assert.equal(inactive.status, 403);
  assert.equal(providerRequests.length, 1, 'inactive users must not reach DeepSeek');
});

test('chat proxy rejects empty and over-limit current messages without calling DeepSeek', async () => {
  const userId = 'malo-ai-length-user';
  db.saveSubscription({ userId, status: 'active', currentPeriodEnd: Date.now() + 60_000 });
  const before = providerRequests.length;

  for (const content of ['', 'x'.repeat(501)]) {
    const response = await request({
      userId,
      messages: [
        { role: 'system', content: 'MalO system prompt' },
        { role: 'user', content },
      ],
    });
    assert.equal(response.status, 400);
    assert.equal((await response.json()).error.code, 'MESSAGE_LENGTH_INVALID');
  }

  assert.equal(providerRequests.length, before);

  const boundary = await request({
    userId,
    messages: [
      { role: 'system', content: 'MalO system prompt' },
      { role: 'user', content: 'x'.repeat(500) },
    ],
  });
  assert.equal(boundary.status, 200, 'a message exactly at the configured limit is accepted');
  assert.equal(providerRequests.length, before + 1);
});

test('chat proxy preserves only the newest history that fits the context character budget', async () => {
  const userId = 'malo-ai-context-user';
  db.saveSubscription({ userId, status: 'active', currentPeriodEnd: Date.now() + 60_000 });
  const history = Array.from({ length: 12 }, (_, index) => ({
    role: 'assistant',
    content: `history-${index}-` + 'x'.repeat(2_500),
  }));
  const before = providerRequests.length;

  const response = await request({
    userId,
    messages: [
      { role: 'system', content: 'MalO system prompt' },
      ...history,
      { role: 'user', content: 'Hi' },
    ],
  });
  assert.equal(response.status, 200);

  const providerBody = JSON.parse(providerRequests[before].init.body);
  assert.equal(providerBody.messages.length, 6);
  assert.match(providerBody.messages[1].content, /^history-8-/);
  assert.equal(
    providerBody.messages.reduce((total, message) => total + message.content.length, 0) <= config.ai.maxContextChars,
    true,
  );
});

test('chat proxy fails fast when the server-side DeepSeek key is missing', async () => {
  const userId = 'malo-ai-unconfigured-user';
  db.saveSubscription({ userId, status: 'active', currentPeriodEnd: Date.now() + 60_000 });
  const previousKey = config.ai.deepseekApiKey;
  config.ai.deepseekApiKey = '';
  const before = providerRequests.length;

  try {
    const response = await request({
      userId,
      messages: [
        { role: 'system', content: 'MalO system prompt' },
        { role: 'user', content: 'Hello' },
      ],
    });
    assert.equal(response.status, 503);
    assert.equal((await response.json()).error.code, 'DEEPSEEK_NOT_CONFIGURED');
    assert.equal(providerRequests.length, before);
  } finally {
    config.ai.deepseekApiKey = previousKey;
  }
});
