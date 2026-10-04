import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import { config } from './config.js';
import { errorHandler } from './middleware.js';
import { catalogRouter } from './routes/catalog.js';
import { cryptoRouter } from './routes/crypto.js';
import { cardsRouter } from './routes/cards.js';
import { subscriptionRouter } from './routes/subscription.js';
import { webhooksRouter } from './routes/webhooks.js';

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', true);

  // Webhooks need the untouched bytes for signature verification, so they are
  // mounted before the JSON body parser.
  app.use(
    ['/api/webhooks/cryptobot', '/api/webhooks/yookassa'],
    express.raw({ type: '*/*', limit: '256kb' }),
  );
  app.use('/api', webhooksRouter);

  app.use(express.json({ limit: '128kb' }));

  app.get('/api/health', (_req, res) =>
    res.json({
      ok: true,
      service: 'malo-payment-gateway',
      version: '1.0.0',
      mock: config.mockProviders,
      providers: {
        yookassa: Boolean(config.yookassa.shopId && config.yookassa.secretKey),
        cryptobot: Boolean(config.cryptobot.token),
      },
      time: new Date().toISOString(),
    }),
  );

  app.use('/api', catalogRouter);
  app.use('/api', cryptoRouter);
  app.use('/api', cardsRouter);
  app.use('/api', subscriptionRouter);

  app.use('/api', (_req, res) =>
    res.status(404).json({ ok: false, error: { code: 'NOT_FOUND', message: 'Unknown endpoint' } }),
  );

  // Static APK download page (keeps the previous Railway behaviour).
  if (config.publicDir && fs.existsSync(config.publicDir)) {
    app.use(express.static(path.resolve(config.publicDir), { extensions: ['html'] }));
  }

  app.use(errorHandler);
  return app;
}
