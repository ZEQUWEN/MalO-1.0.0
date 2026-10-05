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

const boundedInt = (value, fallback, min, max) => Math.min(max, Math.max(min, int(value, fallback)));

const withoutTrailingSlash = (value) => String(value || '').trim().replace(/\/+$/, '');

// Railway exposes RAILWAY_PUBLIC_DOMAIN for a generated public domain. An
// explicit MALO_PUBLIC_URL wins because payment-provider webhooks should be
// registered against a stable custom/generated domain chosen by the operator.
const railwayUrl = process.env.RAILWAY_PUBLIC_DOMAIN
  ? `https://${String(process.env.RAILWAY_PUBLIC_DOMAIN).trim()}`
  : '';

export const config = {
  port: int(process.env.PORT, 8080),
  publicDir: process.env.PUBLIC_DIR || null,
  dataDir: process.env.MALO_DATA_DIR || './.data',

  /** Shared secret the Android client sends in the `X-MalO-Client-Key` header. */
  clientKey: String(process.env.MALO_CLIENT_KEY || '').trim(),
  // Only for an isolated local demo. Production app-facing payment endpoints
  // fail closed if MALO_CLIENT_KEY is absent.
  allowInsecureClientAuth: bool(process.env.MALO_ALLOW_INSECURE_CLIENT_AUTH, false),

  /**
   * Public base URL of this gateway, used to build webhook/return URLs. Set
   * MALO_PUBLIC_URL explicitly in Railway Variables so a domain change cannot
   * silently leave Crypto Pay pointing at an old deployment.
   */
  publicUrl: withoutTrailingSlash(process.env.MALO_PUBLIC_URL || railwayUrl || 'https://malo.up.railway.app'),

  subscription: {
    planId: 'malo_pro_monthly',
    planName: 'MalO Pro (DeepSeek Boundless)',
    periodDays: boundedInt(process.env.MALO_PLAN_PERIOD_DAYS, 30, 1, 366),
    priceRub: process.env.MALO_PRICE_RUB || '499.00',
    priceUsd: process.env.MALO_PRICE_USD || '4.99',
  },

  yookassa: {
    shopId: process.env.YOOKASSA_SHOP_ID || '',
    secretKey: process.env.YOOKASSA_SECRET_KEY || '',
    apiBase: withoutTrailingSlash(process.env.YOOKASSA_API_BASE || 'https://api.yookassa.ru/v3'),
    returnUrl: process.env.YOOKASSA_RETURN_URL || 'malo://payment/return',
    bankCardEnabled: bool(process.env.YOOKASSA_BANK_CARD_ENABLED, true),
    sbpEnabled: bool(process.env.YOOKASSA_SBP_ENABLED, true),
    requestTimeoutMs: boundedInt(process.env.YOOKASSA_REQUEST_TIMEOUT_MS, 5_000, 1_000, 5_000),
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
    token: String(process.env.CRYPTOBOT_TOKEN || '').trim(),
    /** https://pay.crypt.bot/api for mainnet, https://testnet-pay.crypt.bot/api for testnet. */
    apiBase: withoutTrailingSlash(process.env.CRYPTOBOT_API_BASE || 'https://pay.crypt.bot/api'),
    paidBtnUrl: process.env.CRYPTOBOT_PAID_BTN_URL || 'https://t.me/CryptoBot',
    // Crypto Pay accepts 1..2,678,400 seconds. A bounded value prevents a
    // typo in Railway Variables from creating immediately-invalid invoices.
    invoiceExpiresIn: boundedInt(process.env.CRYPTOBOT_INVOICE_EXPIRES_IN, 3600, 1, 2_678_400),
    // Avoid keeping a Crypto Pay webhook open indefinitely. A 5xx response is
    // intentional here: Crypto Pay retries a delivery that was not verified.
    requestTimeoutMs: boundedInt(process.env.CRYPTOBOT_REQUEST_TIMEOUT_MS, 5_000, 1_000, 5_000),
  },

  /** Never hit a real provider in tests / local demos. Never enable on Railway. */
  mockProviders: bool(process.env.MALO_MOCK_PROVIDERS, false),
};

export const isYooKassaConfigured = () =>
  Boolean(config.yookassa.shopId && config.yookassa.secretKey) || config.mockProviders;

export const isYooKassaMethodAvailable = (method) => {
  if (!isYooKassaConfigured()) return false;
  if (method === 'bank_card') return config.yookassa.bankCardEnabled;
  if (method === 'sbp') return config.yookassa.sbpEnabled;
  return false;
};

// A Crypto Pay webhook must always be HMAC-authenticated, including in mock
// mode. Therefore mock mode still needs a non-empty test token.
export const isCryptoBotConfigured = () => Boolean(config.cryptobot.token);
