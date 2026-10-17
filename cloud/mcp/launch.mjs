import fs from 'node:fs';

// Keep the credential outside the repository and Codex command-line arguments.
const connectionFile = process.argv[2];
if (!connectionFile) throw new Error('Pass the private AI connection JSON path');
const connection = JSON.parse(fs.readFileSync(connectionFile, 'utf8'));
if (typeof connection.aiToken !== 'string' || connection.aiToken.length < 32) throw new Error('Invalid AI read-only connection');
process.env.LIFE_CLOCK_AI_URL = connection.serverUrl;
process.env.LIFE_CLOCK_AI_TOKEN = connection.aiToken;
await import('./server.mjs');
