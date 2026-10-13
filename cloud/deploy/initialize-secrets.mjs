import fs from 'node:fs';
import { randomBytes } from 'node:crypto';
const envPath = '/etc/life-clock/service.env';
const connectionPath = '/home/ubuntu/life-clock-connection.json';
if (!fs.existsSync(envPath)) {
  const writeToken = randomBytes(32).toString('base64url');
  const readToken = randomBytes(32).toString('base64url');
  fs.mkdirSync('/etc/life-clock', { recursive: true, mode: 0o750 });
  fs.writeFileSync(envPath, `LIFE_CLOCK_WRITE_TOKEN=${writeToken}\nLIFE_CLOCK_READ_TOKEN=${readToken}\nLIFE_CLOCK_DATA_DIR=/var/lib/life-clock/data\nLIFE_CLOCK_WEB_DIR=/opt/life-clock/app/dist/web\nLIFE_CLOCK_PORT=4178\n`, { mode: 0o600 });
  fs.writeFileSync(connectionPath, JSON.stringify({ serverUrl: 'https://124.221.50.129', writeToken, readToken }, null, 2), { mode: 0o600 });
  console.log('Private connection files created; credentials are not printed.');
} else console.log('Existing server credentials preserved.');
