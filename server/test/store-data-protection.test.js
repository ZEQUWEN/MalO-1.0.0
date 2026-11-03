import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { initStore } from '../src/store.js';

test('corrupt legacy state is never silently replaced or destroyed', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'malo-corrupt-store-'));
  const file = path.join(dir, 'gateway-state.json');
  const contents = '{"subscriptions": damaged';
  fs.writeFileSync(file, contents);
  try {
    assert.throws(() => initStore(dir), /file was not modified/);
    assert.equal(fs.readFileSync(file, 'utf8'), contents);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
