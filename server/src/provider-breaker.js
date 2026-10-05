const FAILURE_THRESHOLD = 3;
const COOLDOWN_MS = 30_000;
const breakers = new Map();

export function isProviderCircuitOpen(provider, now = Date.now()) {
  const state = breakers.get(provider);
  return Boolean(state && state.openedUntil > now);
}

export class ProviderCircuitOpenError extends Error {
  constructor(provider, retryAfter) {
    super(`${provider} is temporarily unavailable`);
    this.name = 'ProviderCircuitOpenError';
    this.status = 503;
    this.code = `${provider.toUpperCase()}_CIRCUIT_OPEN`;
    this.retryAfter = retryAfter;
  }
}

/**
 * Prevents repeated outbound requests from waiting on a failing provider.
 * Payment POSTs are never retried; after three provider/network failures the
 * circuit rejects requests for 30 seconds, then permits one probe.
 */
export async function withProviderCircuitBreaker(
  provider,
  operation,
  { shouldTrip = (error) => error?.status >= 500, failureThreshold = FAILURE_THRESHOLD, cooldownMs = COOLDOWN_MS } = {},
) {
  const now = Date.now();
  const state = breakers.get(provider) || { failures: 0, openedUntil: 0, probeInFlight: false };
  breakers.set(provider, state);

  let probe = false;
  if (state.openedUntil > now) {
    throw new ProviderCircuitOpenError(provider, Math.max(1, Math.ceil((state.openedUntil - now) / 1000)));
  }
  if (state.openedUntil > 0) {
    if (state.probeInFlight) throw new ProviderCircuitOpenError(provider, 1);
    state.probeInFlight = true;
    probe = true;
  }

  try {
    const result = await operation();
    state.failures = 0;
    state.openedUntil = 0;
    return result;
  } catch (error) {
    if (shouldTrip(error)) {
      state.failures += 1;
      if (probe || state.failures >= failureThreshold) {
        state.openedUntil = Date.now() + cooldownMs;
        state.failures = 0;
      }
    } else if (probe) {
      state.failures = 0;
      state.openedUntil = 0;
    }
    throw error;
  } finally {
    if (probe) state.probeInFlight = false;
  }
}
