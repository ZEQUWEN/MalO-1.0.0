import { Router } from 'express';
import { config, isCryptoBotConfigured, isYooKassaConfigured } from '../config.js';
import { buildCatalog } from '../networks.js';

export const catalogRouter = Router();

/**
 * Everything the client needs to render the checkout: plan price, accepted card
 * brands and the crypto asset/network matrix.
 */
catalogRouter.get('/catalog', (_req, res) => {
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
      enabled: isYooKassaConfigured(),
      provider: 'yookassa',
      currency: 'RUB',
      brands: ['VISA', 'MASTERCARD', 'MIR', 'MAESTRO', 'UNIONPAY', 'JCB'],
      paymentMethods: ['bank_card', 'sbp'],
      supportsSavedCards: true,
      // СБП is a redirect, one-time payment flow. Auto-renewal is charged
      // against a separately saved bank-card payment method only.
      supportsSbp: true,
    },
    crypto: {
      enabled: isCryptoBotConfigured(),
      provider: 'cryptobot',
      assets: buildCatalog(),
    },
  });
});
