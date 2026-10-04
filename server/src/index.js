import { config } from './config.js';
import { initStore } from './store.js';
import { createApp, webhookUrls } from './app.js';
import { cryptobot } from './providers/cryptobot.js';

initStore();

const app = createApp();

app.listen(config.port, '0.0.0.0', () => {
  console.log(`[malo-gateway] listening on 0.0.0.0:${config.port}`);
  console.log(`[malo-gateway] public url: ${config.publicUrl}`);
  console.log(`[malo-gateway] mock providers: ${config.mockProviders}`);
  const hooks = webhookUrls();
  console.log(`[malo-gateway] cryptobot webhook: ${hooks.cryptobot}`);
  console.log(`[malo-gateway] yookassa webhook:  ${hooks.yookassa}`);
  if (!config.cryptobot.token) {
    console.log('[malo-gateway] note: CRYPTOBOT_TOKEN is not set — crypto checkout is disabled.');
  } else if (config.mockProviders) {
    console.log('[malo-gateway] WARNING: MALO_MOCK_PROVIDERS is enabled. Never use it in Railway production.');
  } else {
    // Fail visibly in Railway logs when a token from another app/environment is
    // pasted into Variables. The server still starts so the health endpoint
    // remains available, while checkout/webhook processing will fail closed.
    cryptobot
      .getMe()
      .then((appInfo) => console.log(`[malo-gateway] Crypto Pay verified for app ${appInfo?.app_id ?? 'unknown'}.`))
      .catch((error) => console.error(`[malo-gateway] Crypto Pay verification failed: ${error.code || error.message}`));
  }
  if (!config.yookassa.shopId || !config.yookassa.secretKey) {
    console.log('[malo-gateway] note: YOOKASSA_SHOP_ID/SECRET_KEY are not set — card checkout is disabled.');
  }
});
