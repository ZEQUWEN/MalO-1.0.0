import test from 'node:test';
import assert from 'node:assert/strict';
import { ProviderCircuitOpenError, withProviderCircuitBreaker } from '../src/provider-breaker.js';

test('a provider circuit opens after repeated failures and permits one probe after cooldown', async () => {
  const provider = `breaker-test-${Date.now()}`;
  const fail = async () => {
    throw Object.assign(new Error('provider failed'), { status: 502 });
  };

  await assert.rejects(
    withProviderCircuitBreaker(provider, fail, { failureThreshold: 2, cooldownMs: 25 }),
    /provider failed/,
  );
  await assert.rejects(
    withProviderCircuitBreaker(provider, fail, { failureThreshold: 2, cooldownMs: 25 }),
    /provider failed/,
  );
  await assert.rejects(
    withProviderCircuitBreaker(provider, async () => 'must not run'),
    ProviderCircuitOpenError,
  );

  await new Promise((resolve) => setTimeout(resolve, 35));
  assert.equal(await withProviderCircuitBreaker(provider, async () => 'recovered'), 'recovered');
  assert.equal(await withProviderCircuitBreaker(provider, async () => 'closed'), 'closed');
});

test('a single half-open probe is allowed at a time', async () => {
  const provider = `breaker-probe-test-${Date.now()}`;
  const fail = async () => {
    throw Object.assign(new Error('provider failed'), { status: 502 });
  };

  await assert.rejects(withProviderCircuitBreaker(provider, fail, { failureThreshold: 1, cooldownMs: 20 }));
  await new Promise((resolve) => setTimeout(resolve, 30));

  let finishProbe;
  const probe = withProviderCircuitBreaker(
    provider,
    () => new Promise((resolve) => { finishProbe = resolve; }),
    { failureThreshold: 1, cooldownMs: 20 },
  );
  await new Promise((resolve) => setImmediate(resolve));
  await assert.rejects(
    withProviderCircuitBreaker(provider, async () => 'must not run'),
    ProviderCircuitOpenError,
  );

  finishProbe('recovered');
  assert.equal(await probe, 'recovered');
});
