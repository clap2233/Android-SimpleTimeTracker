import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('STDIO MCP pins reads, rejects writes and resets a failed analysis without exposing credentials', async () => {
  const first = '11111111-1111-4111-8111-111111111111';
  const requests = [
    { method: 'initialize', params: { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'test', version: '1' } } },
    { method: 'tools/list' },
    { method: 'tools/call', params: { name: 'query_snapshot', arguments: { collection: 'records' } } },
    { method: 'tools/call', params: { name: 'begin_analysis', arguments: {} } },
    { method: 'tools/call', params: { name: 'query_snapshot', arguments: { collection: 'records', offset: 2, limit: 3 } } },
    { method: 'tools/call', params: { name: 'query_snapshot', arguments: { collection: 'photos' } } },
    { method: 'tools/call', params: { name: 'query_snapshot', arguments: { collection: 'records', url: 'https://evil.test' } } },
    { method: 'tools/call', params: { name: 'delete_snapshot', arguments: {} } },
    { method: 'tools/call', params: { name: 'begin_analysis', arguments: { snapshotId: 'ffffffff-ffff-4fff-8fff-ffffffffffff' } } },
    { method: 'tools/call', params: { name: 'query_snapshot', arguments: { collection: 'records' } } },
    { method: 'ping' },
  ];
  const child = spawn(process.execPath, ['--import', new URL('./fixtures/mcp-fetch.mjs', import.meta.url).href, fileURLToPath(new URL('../mcp/server.mjs', import.meta.url))],
    { env: { ...process.env, LIFE_CLOCK_AI_URL: 'https://example.test', LIFE_CLOCK_AI_TOKEN: 'a'.repeat(32) }, windowsHide: true });
  let stdout = '', stderr = '';
  child.stdout.setEncoding('utf8').on('data', text => { stdout += text; });
  child.stderr.setEncoding('utf8').on('data', text => { stderr += text; });
  const done = new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => { child.kill(); reject(new Error('MCP test timed out')); }, 15000);
    child.on('error', error => { clearTimeout(timeout); reject(error); });
    child.on('exit', code => { clearTimeout(timeout); code === 0 ? resolve() : reject(new Error(stderr)); });
  });
  child.stdin.end(requests.map((request, index) => JSON.stringify({ jsonrpc: '2.0', id: index, ...request })).join('\n') + '\n');
  await done;
  const responses = stdout.trim().split('\n').map(line => JSON.parse(line));
  assert.equal(responses.length, requests.length);
  assert.equal(responses[0].result.protocolVersion, '2025-06-18');
  assert.deepEqual(responses[1].result.tools.map((tool: any) => tool.name), ['begin_analysis', 'read_context', 'query_snapshot']);
  assert.equal(responses[4].result.structuredContent.path, `/api/ai/snapshots/${first}/records`);
  assert.deepEqual(responses[4].result.structuredContent.query, { offset: '2', limit: '3' });
  for (const index of [2, 5, 6, 7, 8, 9]) assert.equal(responses[index].result.isError, true);
  assert.deepEqual(responses[10].result, {});
  assert.equal((stdout + stderr).includes('a'.repeat(32)), false);
});
