// Network boundary fixture, loaded only by the STDIO subprocess test.
const first = '11111111-1111-4111-8111-111111111111';
let latestReads = 0;
globalThis.fetch = async (url, options) => {
  if (url.origin !== 'https://example.test' || options.method !== 'GET' || options.redirect !== 'error' || options.headers.authorization !== `Bearer ${'a'.repeat(32)}`) {
    throw new Error('Unexpected network capability');
  }
  if (url.pathname === '/api/ai/latest') {
    latestReads++;
    return Response.json({ latest: { snapshotId: latestReads === 1 ? first : '22222222-2222-4222-8222-222222222222' } });
  }
  if (url.pathname.includes('ffffffff-ffff-4fff-8fff-ffffffffffff')) return new Response('', { status: 404 });
  return Response.json({ path: url.pathname, query: Object.fromEntries(url.searchParams), snapshotId: first });
};
