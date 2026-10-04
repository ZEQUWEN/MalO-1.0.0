import { config } from './config.js';
import { initStore } from './store.js';
import { createApp } from './app.js';

initStore();

const app = createApp();

app.listen(config.port, '0.0.0.0', () => {
  console.log(`[malo-gateway] listening on 0.0.0.0:${config.port}`);
  console.log(`[malo-gateway] mock providers: ${config.mockProviders}`);
  if (config.publicUrl) {
    console.log(`[malo-gateway] cryptobot webhook: ${config.publicUrl}/api/webhooks/cryptobot`);
    console.log(`[malo-gateway] yookassa webhook:  ${config.publicUrl}/api/webhooks/yookassa`);
  }
});
