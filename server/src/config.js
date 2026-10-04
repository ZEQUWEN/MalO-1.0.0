/**
 * Runtime configuration for the MalO payment gateway.
 *
 * Every secret is read from the environment. Nothing is ever compiled into the
 * Android APK: the app only talks to this gateway over HTTPS.
 */

const bool = (value, fallback = false) => {
  if (value === undefined || value === null || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
};

const int = (value, fallback) => {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const config = {
  port: int(process.env.PORT, 8080),
  publicDir: process.env.PUBLIC_DIR || null,
  dataDir: process.env.MALO_DATA_DIR || './.data',

  /** Shared secret the Android client sends in the `X-MalO-Client-Key` header. */
  clientKey: process.env.MALO_CLIENT_KEY || '',

  /** Public base URL of this gateway, used to build webhook/return URLs. */
  publicUrl: (process.env.MALO_PUBLIC_URL || '').replace(/\/+$/, ''),

  subscription: {
    planId: 'malo_pro_monthly',
    planName: 'MalO Pro (DeepSeek Boundless)',
    periodDays: int(process.env.MALO_PLAN_PERIOD_DAYS, 30),
    priceRub: process.env.MALO_PRICE_RUB || '499.00',
    priceUsd: process.env.MALO_PRICE_USD || '4.99',
  },

  yookassa: {
    shopId: process.env.YOOKASSA_SHOP_ID || '',
    secretKey: process.env.YOOKASSA_SECRET_KEY || '',
    apiBase: process.env.YOOKASSA_API_BASE || 'https://api.yookassa.ru/v3',
    returnUrl: process.env.YOOKASSA_RETURN_URL || 'malo://payment/return',
    /** YooKassa notification source networks (documented by the provider). */
    allowedNetworks: (
      process.env.YOOKASSA_ALLOWED_NETWORKS ||
      '185.71.76.0/27,185.71.77.0/27,77.75.153.0/25,77.75.156.11/32,77.75.156.35/32,77.75.154.128/25,2a02:5180::/32'
    )
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    verifyNetwork: bool(process.env.YOOKASSA_VERIFY_NETWORK, false),
  },

  cryptobot: {
    /** Crypto Pay app token issued by @CryptoBot -> Crypto Pay -> My Apps. */
    token: process.env.CRYPTOBOT_TOKEN || '',
    /** https://pay.crypt.bot/api for mainnet, https://testnet-pay.crypt.bot/api for testnet. */
    apiBase: process.env.CRYPTOBOT_API_BASE || 'https://pay.crypt.bot/api',
    paidBtnUrl: process.env.CRYPTOBOT_PAID_BTN_URL || 'https://t.me/CryptoBot',
    invoiceExpiresIn: int(process.env.CRYPTOBOT_INVOICE_EXPIRES_IN, 3600),
    /** Reject webhook deliveries whose `request_date` is older than this (seconds). */
    webhookMaxAgeSeconds: int(process.env.CRYPTOBOT_WEBHOOK_MAX_AGE, 300),
  },

  /** Never hit a real provider in tests / local demos. */
  mockProviders: bool(process.env.MALO_MOCK_PROVIDERS, false),
};

export const isYooKassaConfigured = () =>
  Boolean(config.yookassa.shopId && config.yookassa.secretKey) || config.mockProviders;

export const isCryptoBotConfigured = () => Boolean(config.cryptobot.token) || config.mockProviders;
