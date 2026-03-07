// Cloudflare Worker — CORS proxy for adsb.fi flight data
// Deploy: npx wrangler deploy
//
// Usage: GET https://<worker>.workers.dev/lat/{lat}/lon/{lon}/dist/{nm}
// Proxies to adsb.fi opendata API and adds CORS headers.

const ADSB_BASE = 'https://opendata.adsb.fi/api/v2';

const CORS_HEADERS = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Access-Control-Max-Age': '86400',
};

export default {
    async fetch(request) {
        // Handle CORS preflight
        if (request.method === 'OPTIONS') {
            return new Response(null, { status: 204, headers: CORS_HEADERS });
        }

        const url = new URL(request.url);
        const path = url.pathname;

        // Only allow /lat/.../lon/.../dist/... pattern
        if (!/^\/lat\/[\d.-]+\/lon\/[\d.-]+\/dist\/\d+$/.test(path)) {
            return new Response('Not found', { status: 404, headers: CORS_HEADERS });
        }

        try {
            const upstream = `${ADSB_BASE}${path}`;
            const resp = await fetch(upstream, {
                headers: { 'User-Agent': 'plane-tracker-rgb/1.0' },
            });

            const body = await resp.text();
            return new Response(body, {
                status: resp.status,
                headers: {
                    'Content-Type': 'application/json',
                    'Cache-Control': 'no-store',
                    ...CORS_HEADERS,
                },
            });
        } catch (e) {
            return new Response(JSON.stringify({ error: e.message }), {
                status: 502,
                headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
            });
        }
    },
};
