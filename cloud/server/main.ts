import { createApp } from './app.js';
const app = await createApp({ dataDir: process.env.LIFE_CLOCK_DATA_DIR ?? './data',
  writeToken: process.env.LIFE_CLOCK_WRITE_TOKEN ?? '', readToken: process.env.LIFE_CLOCK_READ_TOKEN ?? '',
  webDir: process.env.LIFE_CLOCK_WEB_DIR ?? './dist/web' });
await app.listen({ host: '127.0.0.1', port: Number(process.env.LIFE_CLOCK_PORT ?? 4178) });
console.log('Life clock backup service ready on loopback.');
for (const signal of ['SIGINT', 'SIGTERM'] as const) process.on(signal, () => { void app.close().then(() => process.exit(0)); });
