// Plane Tracker — Ubersicht desktop widget
// Solari split-flap board showing live flights overhead
// Uses adsb.fi via Cloudflare Worker proxy

// ─── Configuration ───────────────────────────────────────────────────────────
const FALLBACK_LAT = 51.5074;
const FALLBACK_LON = -0.1278;

// ─── API URLs ────────────────────────────────────────────────────────────────
const PROXY       = 'http://127.0.0.1:41417';
const ADSB_WORKER = 'https://plane-tracker-proxy.plane-tracker-proxy.workers.dev';
const ADSBDB_BASE = 'https://api.adsbdb.com/v0';
const WEATHER_URL = 'https://api.open-meteo.com/v1/forecast';

// ─── Constants ───────────────────────────────────────────────────────────────
const BOX_RADIUS_MILES = 5;
const MIN_ALT_FT       = 1000;
const NM_TO_MI         = 1.15078;
const FLIGHT_CYCLE_MS  = 12000;

const WMO = {
    0:'\u2600\uFE0F', 1:'\uD83C\uDF24\uFE0F', 2:'\u26C5', 3:'\u2601\uFE0F',
    45:'\uD83C\uDF2B\uFE0F', 48:'\uD83C\uDF2B\uFE0F',
    51:'\uD83C\uDF26\uFE0F', 53:'\uD83C\uDF26\uFE0F', 55:'\uD83C\uDF27\uFE0F',
    61:'\uD83C\uDF27\uFE0F', 63:'\uD83C\uDF27\uFE0F', 65:'\uD83C\uDF27\uFE0F',
    71:'\uD83C\uDF28\uFE0F', 73:'\u2744\uFE0F', 75:'\u2744\uFE0F', 77:'\uD83C\uDF28\uFE0F',
    80:'\uD83C\uDF26\uFE0F', 81:'\uD83C\uDF27\uFE0F', 82:'\u26C8\uFE0F',
    85:'\uD83C\uDF28\uFE0F', 86:'\u2744\uFE0F',
    95:'\u26C8\uFE0F', 96:'\u26C8\uFE0F', 99:'\u26C8\uFE0F',
};

// ─── Pure utility functions ──────────────────────────────────────────────────
function haversine(lat1, lon1, lat2, lon2) {
    const R = 3958.8;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat/2)**2
            + Math.cos(lat1*Math.PI/180) * Math.cos(lat2*Math.PI/180) * Math.sin(dLon/2)**2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
}

function toCardinal(deg) {
    return ['N','NE','E','SE','S','SW','W','NW'][Math.round(deg/45) % 8];
}

function cubicPt(t, p0, p1, p2, p3) {
    const u = 1 - t;
    return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
}

function cubicDeriv(t, p0, p1, p2, p3) {
    const u = 1 - t;
    return 3*u*u*(p1-p0) + 6*u*t*(p2-p1) + 3*t*t*(p3-p2);
}

function fmtTime(d) {
    let h = d.getHours(), m = String(d.getMinutes()).padStart(2,'0');
    const ap = h >= 12 ? 'PM' : 'AM';
    h = h % 12 || 12;
    return `${h}:${m} ${ap}`;
}

function fmtDate(d) {
    return d.toLocaleDateString('en-GB', { weekday:'short', day:'numeric', month:'short' });
}

function fmtDayShort(iso) {
    return new Date(iso + 'T12:00:00').toLocaleDateString('en-GB', { weekday:'short' }).toUpperCase();
}

function toTemp(c)  { return Math.round(c * 9/5 + 32); }
function pressFmt(hPa) { return `${(hPa * 0.02953).toFixed(2)} inHg`; }
function distFmt(mi) { return mi.toFixed(1); }

function parseRoute(json) {
    const fr = json?.response?.flightroute;
    if (!fr) return null;
    return {
        origin:      fr.origin?.iata_code      || fr.origin?.icao_code      || null,
        dest:        fr.destination?.iata_code || fr.destination?.icao_code || null,
        originName:  fr.origin?.municipality   || fr.origin?.name           || null,
        destName:    fr.destination?.municipality || fr.destination?.name   || null,
        originLat:   fr.origin?.latitude       || null,
        originLon:   fr.origin?.longitude      || null,
        destLat:     fr.destination?.latitude  || null,
        destLon:     fr.destination?.longitude || null,
        airlineName: fr.airline?.name          || null,
        airlineIata: fr.airline?.iata          || null,
    };
}

function parseAircraft(json) {
    const ac = json?.response?.aircraft;
    if (!ac) return null;
    return { type: ac.type || ac.icao_type || null, manufacturer: ac.manufacturer || null };
}

// ─── Arc geometry (pure computation) ─────────────────────────────────────────
function computeArc(f, route, W, H, lat, lon) {
    const padX  = Math.round(W * 0.06);
    const xOrig = padX;
    const xDest = W - padX;
    const yGnd  = H - 14;
    const planeSize = Math.max(16, Math.round(H * 0.20));

    const CLIMB_F = 0.18, DESCENT_F = 0.22, CRUISE_F = 1 - 0.18 - 0.22;

    const xTOC = xOrig + (xDest - xOrig) * CLIMB_F;
    const xTOD = xDest - (xDest - xOrig) * DESCENT_F;

    const descentW = xDest - xTOD;
    const maxDropH = descentW * 2.8;
    const yCrz = Math.max(Math.round(H * 0.08), Math.round(yGnd - maxDropH));

    const ccp1x = xOrig + (xTOC - xOrig) * 0.1, ccp1y = yGnd;
    const ccp2x = xTOC - (xTOC - xOrig) * 0.1,  ccp2y = yCrz;
    const dcp1x = xTOD + (xDest - xTOD) * 0.1, dcp1y = yCrz;
    const dcp2x = xDest - (xDest - xTOD) * 0.1, dcp2y = yGnd - (yGnd - yCrz) * 0.15;

    const pathD = [
        `M ${xOrig},${yGnd}`,
        `C ${ccp1x},${ccp1y} ${ccp2x},${ccp2y} ${xTOC},${yCrz}`,
        `L ${xTOD},${yCrz}`,
        `C ${dcp1x},${dcp1y} ${dcp2x},${dcp2y} ${xDest},${yGnd}`,
    ].join(' ');

    const hasData = !!(route?.originLat && route?.destLat);
    let pct = 50;
    if (hasData) {
        const total = haversine(route.originLat, route.originLon, route.destLat, route.destLon);
        if (total > 10) {
            const flown = haversine(route.originLat, route.originLon, f.lat, f.lon);
            pct = Math.max(2, Math.min(98, (flown / total) * 100));
        }
    }

    let px, py, angle;
    const tn = pct / 100;
    if (tn <= CLIMB_F) {
        const t = tn / CLIMB_F;
        px    = cubicPt(t, xOrig, ccp1x, ccp2x, xTOC);
        py    = cubicPt(t, yGnd,  ccp1y, ccp2y, yCrz);
        angle = Math.atan2(cubicDeriv(t, yGnd, ccp1y, ccp2y, yCrz),
                           cubicDeriv(t, xOrig, ccp1x, ccp2x, xTOC)) * 180 / Math.PI;
    } else if (tn <= CLIMB_F + CRUISE_F) {
        const t = (tn - CLIMB_F) / CRUISE_F;
        px    = xTOC + (xTOD - xTOC) * t;
        py    = yCrz;
        angle = 0;
    } else {
        const t = (tn - CLIMB_F - CRUISE_F) / DESCENT_F;
        px    = cubicPt(t, xTOD, dcp1x, dcp2x, xDest);
        py    = cubicPt(t, yCrz, dcp1y, dcp2y, yGnd);
        angle = Math.atan2(cubicDeriv(t, yCrz, dcp1y, dcp2y, yGnd),
                           cubicDeriv(t, xTOD, dcp1x, dcp2x, xDest)) * 180 / Math.PI;
    }

    const clipW = Math.max(px + 2, 0);
    const climbPct = CLIMB_F * 100;
    const cruiseEndPct = (CLIMB_F + CRUISE_F) * 100;

    return { pathD, xOrig, xDest, yGnd, yCrz, xTOC, xTOD, px, py, angle, clipW, planeSize, hasData, climbPct, cruiseEndPct, W, H };
}

// ─── Module-level state ref (so command can access state) ────────────────────
let _state = { lat: FALLBACK_LAT, lon: FALLBACK_LON, routeCache: {}, aircraftCache: {}, weatherCycle: 0 };

// ─── Ubersicht: initial state ────────────────────────────────────────────────
export const initialState = {
    lat: FALLBACK_LAT, lon: FALLBACK_LON, locationReady: false,
    flights: [], fidx: 0,
    routeCache: {}, aircraftCache: {},
    weather: null, weatherCycle: 0,
    mode: 'clock',
    lastUpdate: null, error: null,
};

// ─── Ubersicht: init ─────────────────────────────────────────────────────────
export const init = (dispatch) => {
    // Get location via Ubersicht's built-in CoreLocation API
    if (window.geolocation) {
        window.geolocation.getCurrentPosition((pos) => {
            dispatch({ type: 'LOCATION_SET', lat: pos.coords.latitude, lon: pos.coords.longitude });
        });
        // Fallback after 5s if geolocation doesn't respond
        setTimeout(() => {
            if (!_state || !_state.locationReady) {
                dispatch({ type: 'LOCATION_SET', lat: FALLBACK_LAT, lon: FALLBACK_LON });
            }
        }, 5000);
    } else {
        dispatch({ type: 'LOCATION_SET', lat: FALLBACK_LAT, lon: FALLBACK_LON });
    }

    // Flight cycling timer
    setInterval(() => dispatch({ type: 'CYCLE_FLIGHT' }), FLIGHT_CYCLE_MS);
};

// ─── Ubersicht: refresh frequency ────────────────────────────────────────────
export const refreshFrequency = 60000;

// ─── Ubersicht: command ──────────────────────────────────────────────────────
export const command = (dispatch) => {
    if (!_state || !_state.lat || !_state.lon) return;
    const { lat, lon, routeCache, aircraftCache, weatherCycle } = _state;

    const distNm = Math.ceil(BOX_RADIUS_MILES / NM_TO_MI);
    const flightUrl = `${PROXY}/${ADSB_WORKER}/lat/${lat.toFixed(4)}/lon/${lon.toFixed(4)}/dist/${distNm}`;

    fetch(flightUrl)
        .then(r => { if (!r.ok) throw new Error(`API ${r.status}`); return r.json(); })
        .then(json => {
            const raw = json.ac || json.aircraft || [];
            const flights = raw
                .filter(a => a.lat != null && a.lon != null && a.alt_baro !== 'ground' && typeof a.alt_baro === 'number')
                .map(a => ({
                    icao24:   a.hex,
                    callsign: (a.flight || '').trim() || a.hex.toUpperCase(),
                    lat: a.lat, lon: a.lon,
                    altFt:    a.alt_baro || a.alt_geom || 0,
                    spd:      Math.round(a.gs || 0),
                    dir:      toCardinal(a.track || 0),
                    vrateFpm: Math.round(a.baro_rate || 0),
                    distMi:   a.dst != null ? a.dst * NM_TO_MI : haversine(lat, lon, a.lat, a.lon),
                    type:     a.t || '',
                    reg:      a.r || '',
                }))
                .filter(f => f.altFt >= MIN_ALT_FT)
                .sort((a, b) => a.distMi - b.distMi)
                .slice(0, 5);

            // Fetch routes for uncached callsigns
            const routePromises = flights
                .filter(f => !(f.callsign in routeCache))
                .map(f => fetch(`${PROXY}/${ADSBDB_BASE}/callsign/${encodeURIComponent(f.callsign)}`)
                    .then(r => r.ok ? r.json() : null)
                    .then(j => ({ callsign: f.callsign, route: parseRoute(j) }))
                    .catch(() => ({ callsign: f.callsign, route: null }))
                );

            // Fetch aircraft types for uncached hex codes without adsb.fi type
            const acPromises = flights
                .filter(f => !(f.icao24 in aircraftCache) && !f.type)
                .map(f => fetch(`${PROXY}/${ADSBDB_BASE}/aircraft/${encodeURIComponent(f.icao24)}`)
                    .then(r => r.ok ? r.json() : null)
                    .then(j => ({ icao24: f.icao24, info: parseAircraft(j) }))
                    .catch(() => ({ icao24: f.icao24, info: null }))
                );

            // Weather every ~10 cycles (~10 min)
            const shouldWeather = (weatherCycle % 10 === 0);
            const weatherP = shouldWeather
                ? fetch(`${PROXY}/${WEATHER_URL}?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}&current=temperature_2m,relative_humidity_2m,weather_code,surface_pressure&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto`)
                    .then(r => r.json()).catch(() => null)
                : Promise.resolve(null);

            return Promise.all([Promise.all(routePromises), Promise.all(acPromises), weatherP])
                .then(([routes, aircraft, weather]) => {
                    dispatch({ type: 'DATA_LOADED', flights, newRoutes: routes, newAircraft: aircraft, weather });
                });
        })
        .catch(err => dispatch({ type: 'FETCH_ERROR', error: err.message }));
};

// ─── Ubersicht: updateState ──────────────────────────────────────────────────
export const updateState = (event, prev) => {
    if (!prev) prev = initialState;
    let next;

    switch (event.type) {
        case 'LOCATION_SET':
            next = { ...prev, lat: event.lat, lon: event.lon, locationReady: true };
            break;

        case 'DATA_LOADED': {
            const rc = { ...prev.routeCache };
            (event.newRoutes || []).forEach(r => { rc[r.callsign] = r.route; });

            const ac = { ...prev.aircraftCache };
            event.flights.forEach(f => {
                if (f.type && !ac[f.icao24]) ac[f.icao24] = { type: f.type };
            });
            (event.newAircraft || []).forEach(a => { if (a.info) ac[a.icao24] = a.info; });

            const mode = event.flights.length > 0 ? 'flight' : 'clock';
            next = {
                ...prev,
                flights: event.flights,
                fidx: 0,
                routeCache: rc, aircraftCache: ac,
                weather: event.weather || prev.weather,
                weatherCycle: prev.weatherCycle + 1,
                mode,
                lastUpdate: fmtTime(new Date()),
                error: null,
            };
            break;
        }

        case 'CYCLE_FLIGHT':
            if (prev.flights.length <= 1) { next = prev; break; }
            next = { ...prev, fidx: (prev.fidx + 1) % prev.flights.length };
            break;

        case 'FETCH_ERROR':
            next = { ...prev, error: event.error };
            break;

        default:
            next = prev;
    }

    _state = next;
    return next;
};

// ─── Render sub-components ───────────────────────────────────────────────────

function Flap({ ch, lg }) {
    if (ch === ' ') return <span style={{ width: 6, display: 'inline-block' }} />;
    return <span className={lg ? 'flap flap-lg' : 'flap'}>{ch}</span>;
}

function FlapText({ text, lg }) {
    return <span>{[...(text || '')].map((ch, i) => <Flap key={i} ch={ch} lg={lg} />)}</span>;
}

function Header({ now }) {
    return (
        <div className="header">
            <span className="hd-logo">{'\u2708'} PLANE TRACKER</span>
            <div className="hd-right">
                <span className="hd-time">{fmtTime(now)}</span>
                <span className="hd-date">{fmtDate(now)}</span>
            </div>
        </div>
    );
}

function ForecastGrid({ weather }) {
    if (!weather?.daily) return null;
    const d = weather.daily;
    return (
        <div className="forecast-grid">
            {d.time.slice(0, 3).map((t, i) => (
                <div key={i} className="fc-day">
                    <span className="fc-name">{fmtDayShort(t)}</span>
                    <span className="fc-icon">{WMO[d.weather_code[i]] || '\uD83C\uDF21\uFE0F'}</span>
                    <div className="fc-temps">
                        <span className="fc-max">{toTemp(d.temperature_2m_max[i])}&deg;</span>
                        <span className="fc-min"> {toTemp(d.temperature_2m_min[i])}&deg;</span>
                    </div>
                </div>
            ))}
        </div>
    );
}

function ClockMode({ now, weather }) {
    const cur = weather?.current;
    const tempStr = cur ? `${toTemp(cur.temperature_2m)}\u00b0F` : '--';
    const humStr  = cur ? `${Math.round(cur.relative_humidity_2m)}% RH` : '';
    const pressStr = cur ? pressFmt(cur.surface_pressure) : '';

    return (
        <div className="clock-mode">
            <div className="clock-left">
                <div className="big-time">{fmtTime(now)}</div>
                <div className="weather-line">
                    <span className="temp-val">{tempStr}</span>
                    {humStr && <span> &middot; {humStr}</span>}
                    {pressStr && <span> &middot; {pressStr}</span>}
                </div>
                <div className="no-flights">NO FLIGHTS OVERHEAD</div>
            </div>
            <div className="clock-right">
                <div className="forecast-label">3-DAY FORECAST</div>
                <ForecastGrid weather={weather} />
            </div>
        </div>
    );
}

function RouteArc({ flight, route, lat, lon }) {
    const W = 420, H = 120;
    const arc = computeArc(flight, route, W, H, lat, lon);

    return (
        <div className="arc-container">
            <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: '100%', display: 'block', overflow: 'visible' }}>
                <defs>
                    <linearGradient id="arc-grad" x1="0%" y1="0%" x2="100%" y2="0%">
                        <stop offset="0%" stopColor="#e8e0d0" stopOpacity="1" />
                        <stop offset={`${arc.climbPct}%`} stopColor="#d4a847" stopOpacity="0.9" />
                        <stop offset={`${arc.cruiseEndPct}%`} stopColor="#d4a847" stopOpacity="0.9" />
                        <stop offset="100%" stopColor="#e8e0d0" stopOpacity="1" />
                    </linearGradient>
                    <clipPath id="flown-clip">
                        <rect x="0" y="0" width={arc.clipW} height={H + 10} />
                    </clipPath>
                    <filter id="glow-filter">
                        <feGaussianBlur stdDeviation="2.5" result="blur" />
                        <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
                    </filter>
                </defs>
                <line x1={arc.xOrig} y1={arc.yGnd + 6} x2={arc.xDest} y2={arc.yGnd + 6}
                      stroke="rgba(255,255,255,0.12)" strokeWidth="1" />
                <path d={arc.pathD} stroke="rgba(255,255,255,0.12)" strokeWidth="1.5"
                      strokeDasharray="6,10" fill="none" />
                <path d={arc.pathD} stroke="url(#arc-grad)" strokeWidth="2.5"
                      fill="none" clipPath="url(#flown-clip)" filter="url(#glow-filter)" />
                <line x1={arc.xTOC} y1={arc.yCrz} x2={arc.xTOD} y2={arc.yCrz}
                      stroke="rgba(212,168,71,0.20)" strokeWidth="1" strokeDasharray="2,6" />
                <circle cx={arc.xOrig} cy={arc.yGnd} r="5" fill="#d4a847" opacity="0.25">
                    <animate attributeName="r" values="5;9;5" dur="2.4s" repeatCount="indefinite" />
                    <animate attributeName="opacity" values="0.25;0.05;0.25" dur="2.4s" repeatCount="indefinite" />
                </circle>
                <circle cx={arc.xOrig} cy={arc.yGnd} r="3.5" fill="#d4a847" />
                <circle cx={arc.xDest} cy={arc.yGnd} r="3.5" fill="#e8e0d0"
                        opacity={arc.hasData ? 0.7 : 0.3} />
                <text x={arc.px} y={arc.py}
                      transform={`rotate(${arc.angle}, ${arc.px}, ${arc.py})`}
                      textAnchor="middle" dominantBaseline="central"
                      fontSize={arc.planeSize} fill="#e8e0d0"
                      filter="url(#glow-filter)"
                      style={{ userSelect: 'none' }}>{'\u2708'}</text>
            </svg>
        </div>
    );
}

function FlightMode({ flight, route, ac, fidx, total, lat, lon }) {
    const origCode = route === undefined ? '\u00b7\u00b7\u00b7' : (route?.origin || '???');
    const destCode = route === undefined ? '\u00b7\u00b7\u00b7' : (route?.dest   || '???');
    const typeName = ac?.type || flight.type || '';
    const airlineName = route?.airlineName || '';
    const airlineIata = route?.airlineIata;
    const absFpm = Math.abs(flight.vrateFpm);

    let vsClass, vsText, vfpmText;
    if (flight.vrateFpm > 200) {
        vsClass = 'sc-vs up'; vsText = '\u2191 Climbing';
        vfpmText = `+${absFpm.toLocaleString()} fpm`;
    } else if (flight.vrateFpm < -200) {
        vsClass = 'sc-vs down'; vsText = '\u2193 Descending';
        vfpmText = `\u2212${absFpm.toLocaleString()} fpm`;
    } else {
        vsClass = 'sc-vs lvl'; vsText = '\u2192 Level';
        vfpmText = '';
    }

    const vsCardClass = flight.vrateFpm > 200 ? 'stat-card vs-up'
                      : flight.vrateFpm < -200 ? 'stat-card vs-down'
                      : 'stat-card vs-lvl';

    return (
        <div className="flight-mode">
            {/* Airline band */}
            <div className="fl-airline">
                {airlineIata && (
                    <img className="airline-logo"
                         src={`https://pics.avs.io/80/80/${airlineIata}.png`}
                         onError={(e) => { e.target.style.display = 'none'; }}
                         alt="" />
                )}
                <div className="fl-airline-text">
                    <div className="airline-name">{airlineName}</div>
                    <div className="fl-sub">
                        <span className="fl-callsign"><FlapText text={flight.callsign} /></span>
                        {typeName && <span className="fl-type">&middot; {typeName}</span>}
                        {total > 1 && <span className="fl-page">{fidx + 1} / {total}</span>}
                    </div>
                </div>
            </div>

            {/* Route + arc */}
            <div className="fl-route-section">
                <div className="fl-airports">
                    <div className="ap-block">
                        <span className="ap-iata"><FlapText text={origCode} lg={true} /></span>
                        <span className="ap-city">{route?.originName || ''}</span>
                    </div>
                    <div className="ap-block dest">
                        <span className="ap-iata"><FlapText text={destCode} lg={true} /></span>
                        <span className="ap-city">{route?.destName || ''}</span>
                    </div>
                </div>
                <RouteArc flight={flight} route={route} lat={lat} lon={lon} />
            </div>

            {/* Stats grid */}
            <div className="fl-stats-section">
                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="sc-val-row">
                            <span className="sc-val">{flight.altFt.toLocaleString()}</span>
                            <span className="sc-unit">ft</span>
                        </div>
                        <div className="sc-lbl">ALTITUDE</div>
                    </div>
                    <div className="stat-card">
                        <div className="sc-val-row">
                            <span className="sc-val">{flight.spd}</span>
                            <span className="sc-unit">kts</span>
                        </div>
                        <div className="sc-lbl">GND SPEED</div>
                    </div>
                    <div className="stat-card">
                        <div className="sc-val-row">
                            <span className="sc-val">{distFmt(flight.distMi)}</span>
                            <span className="sc-unit">mi {flight.dir}</span>
                        </div>
                        <div className="sc-lbl">DISTANCE</div>
                    </div>
                    <div className={vsCardClass}>
                        <span className={vsClass}>{vsText}</span>
                        {vfpmText && <div className="sc-vfpm">{vfpmText}</div>}
                        <div className="sc-lbl">CLIMB RATE</div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function Footer({ flights, lastUpdate, error }) {
    const dotColor = error ? '#c05050' : '#5a9a60';
    const msg = error ? `Error: ${error}` : (lastUpdate ? `Updated ${lastUpdate}` : 'Starting\u2026');
    const count = flights.length > 0
        ? `${flights.length} flight${flights.length !== 1 ? 's' : ''} overhead`
        : 'No flights overhead';

    return (
        <div className="footer">
            <div className="ft-status">
                <span className="ft-dot" style={{ background: dotColor }} />
                <span>{msg}</span>
            </div>
            <span className="ft-count">{count}</span>
        </div>
    );
}

// ─── Ubersicht: render ───────────────────────────────────────────────────────
export const render = (state) => {
    const { mode, flights, fidx, routeCache, aircraftCache, weather, error, lastUpdate } = state;
    const now = new Date();

    if (mode === 'loading') {
        return (
            <div className="container">
                <div className="loading">
                    <div className="loading-logo">{'\u2708'} PLANE TRACKER</div>
                    <div className="loading-msg">Acquiring location...</div>
                </div>
            </div>
        );
    }

    const flight = flights[fidx];
    const route  = flight ? routeCache[flight.callsign] : null;
    const ac     = flight ? aircraftCache[flight.icao24] : null;

    return (
        <div className="container">
            <Header now={now} />
            <div className="main">
                {mode === 'clock' || !flight
                    ? <ClockMode now={now} weather={weather} />
                    : <FlightMode flight={flight} route={route} ac={ac}
                                  fidx={fidx} total={flights.length}
                                  lat={state.lat} lon={state.lon} />
                }
            </div>
            <Footer flights={flights} lastUpdate={lastUpdate} error={error} />
        </div>
    );
};

// ─── Ubersicht: className (Emotion CSS) ──────────────────────────────────────
export const className = `
    position: absolute;
    bottom: 40px;
    right: 40px;
    width: 800px;
    height: 480px;
    font-family: 'Courier New', Courier, monospace;
    color: #e8e0d0;
    background: #0c0c10;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 8px 32px rgba(0,0,0,0.6);

    /* Scanline overlay */
    &::after {
        content: '';
        position: absolute;
        inset: 0;
        background: repeating-linear-gradient(
            0deg, transparent, transparent 2px,
            rgba(0,0,0,0.06) 2px, rgba(0,0,0,0.06) 3px
        );
        pointer-events: none;
        z-index: 9999;
        border-radius: 8px;
    }

    .container {
        display: flex;
        flex-direction: column;
        height: 100%;
        width: 100%;
    }

    /* ── Loading ───────────────────────────────────── */
    .loading {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 14px;
    }
    .loading-logo { font-size: 20px; color: #d4a847; letter-spacing: 4px; }
    .loading-msg  { font-size: 13px; color: #6a6a70; }

    /* ── Header ────────────────────────────────────── */
    .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 8px 16px;
        border-bottom: 1px solid #252530;
        flex-shrink: 0;
    }
    .hd-logo  { font-size: 11px; color: #6a6a70; letter-spacing: 5px; font-weight: normal; }
    .hd-right { display: flex; align-items: baseline; gap: 8px; }
    .hd-time  { font-size: 17px; color: #d4a847; font-weight: bold; letter-spacing: 1px; }
    .hd-date  { font-size: 11px; color: #6a6a70; }

    /* ── Main ──────────────────────────────────────── */
    .main { flex: 1; display: flex; overflow: hidden; min-height: 0; }

    /* ── Clock mode ────────────────────────────────── */
    .clock-mode { display: flex; width: 100%; }
    .clock-left {
        flex: 1; display: flex; flex-direction: column;
        justify-content: center; align-items: center;
        padding: 8px 20px; border-right: 1px solid #1a1a20; gap: 8px;
    }
    .big-time     { font-size: 58px; color: #d4a847; font-weight: bold; letter-spacing: 2px; }
    .weather-line { font-size: 13px; color: #6a6a70; }
    .temp-val     { color: #e8e0d0; }
    .no-flights   { font-size: 11px; color: #6a6a70; letter-spacing: 3px; margin-top: 6px; }
    .clock-right  {
        width: 52%; display: flex; flex-direction: column;
        justify-content: center; padding: 8px 14px; gap: 6px;
    }
    .forecast-label { font-size: 11px; color: #6a6a70; letter-spacing: 3px; }
    .forecast-grid  { display: flex; gap: 6px; justify-content: space-around; }
    .fc-day  { display: flex; flex-direction: column; align-items: center; gap: 2px; flex: 1; }
    .fc-name { font-size: 11px; color: #d4a847; letter-spacing: 1px; }
    .fc-icon { font-size: 18px; }
    .fc-temps { font-size: 11px; }
    .fc-max  { color: #e8e0d0; }
    .fc-min  { color: #6a6a70; }

    /* ── Flight mode (desktop grid) ────────────────── */
    .flight-mode {
        display: grid;
        grid-template-columns: 58% 42%;
        grid-template-rows: auto 1fr;
        width: 100%;
        min-height: 0;
        overflow: hidden;
    }

    /* ── Airline band ──────────────────────────────── */
    .fl-airline {
        grid-column: 1;
        grid-row: 1;
        display: flex;
        align-items: center;
        gap: 14px;
        padding: 16px 24px;
        border-bottom: 1px solid #1a1a20;
        border-right: 1px solid #1a1a20;
        position: relative;
    }
    .fl-airline::before {
        content: '';
        position: absolute; left: 0; top: 0; bottom: 0;
        width: 4px;
        background: #d4a847;
        opacity: 0.9;
    }
    .airline-logo {
        width: 52px; height: 52px;
        object-fit: contain; flex-shrink: 0;
        border-radius: 3px;
        background: rgba(255,255,255,0.10);
        padding: 4px;
        border: 1px solid rgba(255,255,255,0.15);
    }
    .fl-airline-text { flex: 1; overflow: hidden; min-width: 0; }
    .airline-name {
        font-size: 22px; font-weight: bold;
        color: #e8e0d0; letter-spacing: 0.5px;
        white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .fl-sub { display: flex; align-items: baseline; gap: 10px; margin-top: 4px; }
    .fl-callsign { font-size: 17px; color: #e8e0d0; letter-spacing: 1px; }
    .fl-type     { font-size: 14px; color: #6a6a70; letter-spacing: 0.5px; }
    .fl-page     { font-size: 12px; color: #6a6a70; margin-left: auto; }

    /* ── Route section ─────────────────────────────── */
    .fl-route-section {
        grid-column: 1;
        grid-row: 2;
        border-right: 1px solid #1a1a20;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        padding: 16px 24px 12px;
        overflow: hidden;
        min-height: 0;
        position: relative;
    }
    .fl-route-section::before {
        content: '';
        position: absolute; inset: 0;
        background:
            radial-gradient(ellipse 40% 60% at 10% 80%, rgba(212,168,71,0.06) 0%, transparent 70%),
            radial-gradient(ellipse 40% 60% at 90% 80%, rgba(212,168,71,0.04) 0%, transparent 70%);
        pointer-events: none;
    }
    .fl-airports {
        display: flex; justify-content: space-between;
        align-items: center;
        position: relative; z-index: 1;
        flex: 1;
    }
    .ap-block { display: flex; flex-direction: column; gap: 1px; }
    .ap-block.dest { align-items: flex-end; }
    .ap-iata {
        font-size: 60px;
        font-weight: bold; letter-spacing: 6px; line-height: 1;
    }
    .ap-city {
        font-size: 12px; color: #6a6a70; letter-spacing: 2px;
        text-transform: uppercase;
        max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .ap-block.dest .ap-city { text-align: right; }
    .arc-container {
        position: relative; z-index: 1;
        height: 100px;
        flex: 0 0 auto;
        width: 100%;
        margin-top: 8px;
    }

    /* ── Stats grid ────────────────────────────────── */
    .fl-stats-section {
        grid-column: 2;
        grid-row: 1 / span 2;
    }
    .stats-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 1fr 1fr;
        gap: 1px;
        background: #1a1a20;
        height: 100%;
    }
    .stat-card {
        background: radial-gradient(ellipse 80% 60% at 0% 0%, rgba(212,168,71,0.04) 0%, transparent 60%), #0c0c10;
        padding: 10px 14px 8px;
        display: flex;
        flex-direction: column;
        justify-content: center;
        position: relative;
        overflow: hidden;
    }
    .stat-card::before {
        content: '';
        position: absolute;
        top: 0; left: 0; right: 0;
        height: 2px;
        background: #d4a847;
        opacity: 0.4;
    }
    .stat-card.vs-up::before   { background: #7ab87a; opacity: 0.5; }
    .stat-card.vs-down::before { background: #b87a7a; opacity: 0.5; }
    .stat-card.vs-lvl::before  { background: #3a3a44; opacity: 0.3; }
    .stat-card.vs-up   { background: radial-gradient(ellipse 80% 60% at 0% 0%, rgba(122,184,122,0.06) 0%, transparent 60%), #0c0c10; }
    .stat-card.vs-down { background: radial-gradient(ellipse 80% 60% at 0% 0%, rgba(184,122,122,0.06) 0%, transparent 60%), #0c0c10; }

    .sc-val-row { display: flex; align-items: baseline; gap: 5px; }
    .sc-val  { font-size: 36px; font-weight: bold; letter-spacing: 1px; line-height: 1; }
    .sc-unit { font-size: 11px; color: #6a6a70; letter-spacing: 1px; }
    .sc-lbl  { font-size: 13px; letter-spacing: 3px; color: #6a6a70; margin-top: 5px; text-transform: uppercase; }

    .sc-vs { font-size: 18px; font-weight: bold; letter-spacing: 0.5px; line-height: 1.2; }
    .sc-vs.up   { color: #7ab87a; }
    .sc-vs.down { color: #b87a7a; }
    .sc-vs.lvl  { color: #6a6a70; }
    .sc-vfpm { font-size: 11px; color: #6a6a70; margin-top: 3px; letter-spacing: 0.5px; }

    /* ── Split-flap cells ──────────────────────────── */
    .flap {
        display: inline-block;
        background: #1a1a1e;
        color: #e8e0d0;
        padding: 2px 3px;
        border-bottom: 2px solid #d4a847;
        margin: 0 1px;
        position: relative;
        line-height: 1;
        border-radius: 2px;
    }
    .flap::after {
        content: '';
        position: absolute;
        left: 0; right: 0;
        top: 50%;
        height: 1px;
        background: rgba(0,0,0,0.5);
        pointer-events: none;
    }
    .flap-lg {
        padding: 4px 3px;
        margin: 0 2px;
        border-bottom-width: 3px;
    }

    /* ── Footer ────────────────────────────────────── */
    .footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 16px;
        border-top: 1px solid #252530;
        flex-shrink: 0;
    }
    .ft-status { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #6a6a70; }
    .ft-dot {
        width: 8px; height: 8px;
        border-radius: 50%;
        flex-shrink: 0;
    }
    .ft-count { font-size: 11px; color: #6a6a70; }
`;
