import { Router } from 'express';
import {
  config,
  isCryptoBotConfigured,
  isYooKassaMethodAvailable,
} from '../config.js';
import { buildCatalog } from '../networks.js';
import { requireClientKey } from '../middleware.js';
import { isProviderCircuitOpen } from '../provider-breaker.js';

export const catalogRouter = Router();

export function availablePaymentMethods() {
  const methods = [];
  if (isYooKassaMethodAvailable('bank_card') && !isProviderCircuitOpen('yookassa')) {
    methods.push({ id: 'bank_card', provider: 'yookassa' });
  }
  if (isYooKassaMethodAvailable('sbp') && !isProviderCircuitOpen('yookassa')) {
    methods.push({ id: 'sbp', provider: 'yookassa' });
  }
  if (isCryptoBotConfigured() && !isProviderCircuitOpen('cryptobot')) {
    methods.push({ id: 'cryptobot', provider: 'cryptobot' });
  }
  return methods;
}

function availableYooKassaMethods() {
  return availablePaymentMethods()
    .filter((method) => method.provider === 'yookassa')
    .map((method) => method.id);
}

catalogRouter.get('/payment/methods', requireClientKey, (_req, res) => {
  res.json({ ok: true, methods: availablePaymentMethods() });
});

/**
 * Everything the client needs to render the checkout: plan price, accepted card
 * brands and the crypto asset/network matrix.
 */
catalogRouter.get('/catalog', (_req, res) => {
  const yookassaMethods = availableYooKassaMethods();
  res.json({
    ok: true,
    plan: {
      id: config.subscription.planId,
      name: config.subscription.planName,
      periodDays: config.subscription.periodDays,
      priceRub: config.subscription.priceRub,
      priceUsd: config.subscription.priceUsd,
    },
    card: {
      enabled: yookassaMethods.length > 0,
      provider: 'yookassa',
      currency: 'RUB',
      brands: yookassaMethods.includes('bank_card')
        ? ['VISA', 'MASTERCARD', 'MIR', 'MAESTRO', 'UNIONPAY', 'JCB']
        : [],
      paymentMethods: yookassaMethods,
      supportsSavedCards: yookassaMethods.includes('bank_card'),
      // СБП is a redirect, one-time payment flow. Auto-renewal is charged
      // against a separately saved bank-card payment method only.
      supportsSbp: yookassaMethods.includes('sbp'),
    },
    crypto: {
      enabled: isCryptoBotConfigured(),
      provider: 'cryptobot',
      assets: isCryptoBotConfigured() ? buildCatalog() : [],
    },
  });
});
