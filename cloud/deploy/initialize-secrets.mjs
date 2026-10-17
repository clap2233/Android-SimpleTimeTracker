import fs from 'node:fs';
import { randomBytes } from 'node:crypto';
const envPath = '/etc/life-clock/service.env';
const connectionPath = '/home/ubuntu/life-clock-connection.json';
const aiConnectionPath = '/home/ubuntu/life-clock-ai-connection.json';
if (!fs.existsSync(envPath)) {
  const writeToken = randomBytes(32).toString('base64url');
  const readToken = randomBytes(32).toString('base64url');
  fs.mkdirSync('/etc/life-clock', { recursive: true, mode: 0o750 });
  fs.writeFileSync(envPath, `LIFE_CLOCK_WRITE_TOKEN=${writeToken}\nLIFE_CLOCK_READ_TOKEN=${readToken}\nLIFE_CLOCK_DATA_DIR=/var/lib/life-clock/data\nLIFE_CLOCK_WEB_DIR=/opt/life-clock/app/dist/web\nLIFE_CLOCK_PORT=4178\n`, { mode: 0o600 });
  fs.writeFileSync(connectionPath, JSON.stringify({ serverUrl: 'https://124.221.50.129', writeToken, readToken }, null, 2), { mode: 0o600 });
  console.log('Private connection files created; credentials are not printed.');
} else console.log('Existing server credentials preserved.');

// Add an independent text-only credential without rotating the phone or browser credentials.
const existing = fs.readFileSync(envPath, 'utf8');
let aiToken = existing.match(/^LIFE_CLOCK_AI_TOKEN=(\S+)$/m)?.[1];
if (!aiToken) {
  aiToken = randomBytes(32).toString('base64url');
  const temporary = `${envPath}.ai-new`;
  fs.writeFileSync(temporary, `${existing.trimEnd()}\nLIFE_CLOCK_AI_TOKEN=${aiToken}\n`, { mode: 0o600, flag: 'wx' });
  fs.renameSync(temporary, envPath);
}
fs.writeFileSync(aiConnectionPath, JSON.stringify({ serverUrl: 'https://124.221.50.129', aiToken }, null, 2), { mode: 0o600 });
fs.chmodSync(aiConnectionPath, 0o600);
console.log('AI read-only connection file prepared; credential is not printed.');
