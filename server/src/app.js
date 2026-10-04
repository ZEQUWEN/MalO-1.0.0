import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import { config } from './config.js';
import { errorHandler } from './middleware.js';
import { requestLogger } from './logger.js';
import { catalogRouter } from './routes/catalog.js';
import { cryptoRouter } from './routes/crypto.js';
import { cardsRouter } from './routes/cards.js';
import { subscriptionRouter } from './routes/subscription.js';
import { webhooksRouter } from './routes/webhooks.js';

/** Absolute webhook URLs to register with each provider. */
export function webhookUrls(base = config.publicUrl) {
  const origin = (base || '').replace(/\/+$/, '');
  return {
    cryptobot: `${origin}/api/webhooks/cryptobot`,
    yookassa: `${origin}/api/webhooks/yookassa`,
  };
}

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', true);

  app.use(requestLogger);

  // Webhooks need the untouched bytes for signature verification, so the raw
  // parser is mounted on their paths only — before the JSON body parser.
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
      publicUrl: config.publicUrl,
      providers: {
        yookassa: Boolean(config.yookassa.shopId && config.yookassa.secretKey),
        cryptobot: Boolean(config.cryptobot.token),
      },
      webhooks: webhookUrls(),
      time: new Date().toISOString(),
    }),
  );

  /** Convenience endpoint: the exact URLs to paste into each provider. */
  app.get('/api/webhooks', (_req, res) =>
    res.json({
      ok: true,
      publicUrl: config.publicUrl,
      cryptobot: {
        url: webhookUrls().cryptobot,
        register: '@CryptoBot → Crypto Pay → My Apps → Webhooks',
        verification: 'HMAC_SHA256(SHA256(app_token), rawBody) in crypto-pay-api-signature',
        events: ['invoice_paid'],
      },
      yookassa: {
        url: webhookUrls().yookassa,
        register: 'ЮKassa → Интеграция → HTTP-уведомления',
        verification: 'source IP allowlist',
        events: ['payment.succeeded', 'payment.canceled', 'refund.succeeded'],
      },
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
  const publicDir = config.publicDir ? path.resolve(config.publicDir) : null;
  if (publicDir && fs.existsSync(publicDir)) {
    app.use(
      express.static(publicDir, {
        extensions: ['html'],
        setHeaders: (res, filePath) => {
          if (filePath.endsWith('.apk')) {
            res.setHeader('Content-Type', 'application/vnd.android.package-archive');
            res.setHeader('Cache-Control', 'public, max-age=300');
          } else if (/\.(png|ico|webmanifest|svg)$/.test(filePath)) {
            res.setHeader('Cache-Control', 'public, max-age=604800, immutable');
          }
        },
      }),
    );

    // Browsers ask for /favicon.ico even when no <link> points at it. The file
    // is shipped in public/, this is just a quiet fallback.
    app.get('/favicon.ico', (_req, res) => res.status(204).end());
  }

  app.use(errorHandler);
  return app;
}
