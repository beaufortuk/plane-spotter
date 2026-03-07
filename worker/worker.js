// Cloudflare Worker — CORS proxy for adsb.fi flight data
// Deploy: npx wrangler deploy
//
// Usage: GET https://<worker>.workers.dev/lat/{lat}/lon/{lon}/dist/{nm}
// Proxies to adsb.fi opendata API and adds CORS headers.
//
// Rate-limit protection:
//   1. Coords rounded to 1 decimal (~11km) so nearby clients share cache
//   2. 45s cache TTL (CF Cache API) — at most ~1.3 upstream req/min per grid cell
//   3. In-memory stale fallback — if upstream 429s, serve last good response
//   4. Never forwards 429 to client — always returns data or empty array

const ADSB_BASE = 'https://opendata.adsb.fi/api/v2';
const CACHE_TTL = 45; // seconds

const CORS_HEADERS = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
};

// In-memory stale cache — survives across requests within the same isolate.
// Key: normalized path, Value: { body, ts }
const staleCache = new Map();
const STALE_MAX_AGE = 300_000; // serve stale data up to 5 minutes old

function roundCoord(val, decimals = 1) {
    const f = parseFloat(val);
    return isNaN(f) ? val : f.toFixed(decimals);
}

function normalizePath(path) {
    const m = path.match(/^\/lat\/([\d.-]+)\/lon\/([\d.-]+)\/dist\/(\d+)$/);
    if (!m) return null;
    return `/lat/${roundCoord(m[1])}/lon/${roundCoord(m[2])}/dist/${m[3]}`;
}

function jsonResponse(body, status, extra = {}) {
    return new Response(body, {
        status,
        headers: { 'Content-Type': 'application/json', ...CORS_HEADERS, ...extra },
    });
}

export default {
    async fetch(request) {
        if (request.method === 'OPTIONS') {
            return new Response(null, { status: 204, headers: CORS_HEADERS });
        }

        const url = new URL(request.url);
        const normalized = normalizePath(url.pathname);

        if (!normalized) {
            return jsonResponse('"Not found"', 404);
        }

        // 1. Check CF edge cache
        const cache = caches.default;
        const cacheUrl = new URL(url.origin + normalized);
        const cacheKey = new Request(cacheUrl.toString());
        const cached = await cache.match(cacheKey);
        if (cached) {
            const body = await cached.text();
            return jsonResponse(body, 200, { 'X-Cache': 'HIT' });
        }

        // 2. Fetch upstream
        try {
            const upstream = `${ADSB_BASE}${normalized}`;
            const resp = await fetch(upstream, {
                headers: { 'User-Agent': 'plane-tracker-rgb/1.0' },
            });

            if (resp.status === 200) {
                const body = await resp.text();

                // Store in CF edge cache
                const cacheResp = new Response(body, {
                    status: 200,
                    headers: {
                        'Content-Type': 'application/json',
                        'Cache-Control': `s-maxage=${CACHE_TTL}`,
                    },
                });
                await cache.put(cacheKey, cacheResp);

                // Store in memory for stale fallback
                staleCache.set(normalized, { body, ts: Date.now() });

                return jsonResponse(body, 200, { 'X-Cache': 'MISS' });
            }

            // Upstream error (429 or other) — try stale fallback
            const stale = staleCache.get(normalized);
            if (stale && (Date.now() - stale.ts) < STALE_MAX_AGE) {
                return jsonResponse(stale.body, 200, { 'X-Cache': 'STALE' });
            }

            // No stale data — return empty result so client doesn't error
            if (resp.status === 429) {
                const empty = JSON.stringify({ ac: [], msg: 'No recent data', total: 0, now: Date.now() / 1000 });
                return jsonResponse(empty, 200, { 'X-Cache': 'EMPTY' });
            }

            return jsonResponse(await resp.text(), resp.status, { 'X-Cache': 'MISS' });
        } catch (e) {
            // Network error — try stale fallback
            const stale = staleCache.get(normalized);
            if (stale && (Date.now() - stale.ts) < STALE_MAX_AGE) {
                return jsonResponse(stale.body, 200, { 'X-Cache': 'STALE' });
            }
            return jsonResponse(JSON.stringify({ error: e.message }), 502);
        }
    },
};
