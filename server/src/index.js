import { config } from './config.js';
import { initStore } from './store.js';
import { createApp } from './app.js';
import { createV2Services } from './v2/services.js';

initStore();

const app = createApp({ v2: createV2Services() });

app.listen(config.port, '0.0.0.0', () => {
  console.log(`[malo-gateway] listening on 0.0.0.0:${config.port}`);
  console.log(`[malo-gateway] public url: ${config.publicUrl}`);
  console.log(`[malo-gateway] mock providers: ${config.mockProviders}`);
  console.log(`[malo-gateway] Ko-fi webhook: ${config.publicUrl}/api/v2/webhooks/kofi`);
  console.log('[malo-gateway] Legacy payment and userId APIs are disabled.');
});
