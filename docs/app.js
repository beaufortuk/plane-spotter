// ─── Configuration ────────────────────────────────────────────────────────────
let   UNITS              = localStorage.getItem('pt-units') || 'imperial';
const FLIGHT_REFRESH_MS  = 15_000;  // 15s — frequent updates while tracking
const FLIGHT_IDLE_MS     = 15_000;  // 15s — rapid scan when sky is empty
const FLIGHT_BACKOFF_MS  = 30_000;  // back off on error (Worker absorbs 429s)
const WEATHER_REFRESH_MS = 600_000;
const FLIGHT_CYCLE_MS    = 12_000;
const BOX_RADIUS_MILES   = 5;      // 5mi — jets cross 10mi window in ~70s
const MIN_ALT_FT         = 1000;

// ─── WMO weather code → emoji ─────────────────────────────────────────────────
const WMO = {
    0:'☀️', 1:'🌤️', 2:'⛅', 3:'☁️',
    45:'🌫️', 48:'🌫️',
    51:'🌦️', 53:'🌦️', 55:'🌧️',
    61:'🌧️', 63:'🌧️', 65:'🌧️',
    71:'🌨️', 73:'❄️', 75:'❄️', 77:'🌨️',
    80:'🌦️', 81:'🌧️', 82:'⛈️',
    85:'🌨️', 86:'❄️',
    95:'⛈️', 96:'⛈️', 99:'⛈️',
};

// ─── State ────────────────────────────────────────────────────────────────────
let userLat = null, userLon = null;
let flights  = [];
let lastWeather = null;
let fidx     = 0;
let cycleTimer = null;
const routeCache    = new Map();
const aircraftCache = new Map();

// ─── Route cache persistence (localStorage with 24h TTL) ─────────────────────
const ROUTE_CACHE_KEY = 'ps-route-cache';
const ROUTE_CACHE_TTL = 24 * 60 * 60 * 1000; // 24 hours
const ROUTE_CACHE_MAX = 500;

function loadRouteCache() {
    try {
        const raw = localStorage.getItem(ROUTE_CACHE_KEY);
        if (!raw) return;
        const entries = JSON.parse(raw);
        const now = Date.now();
        let loaded = 0;
        for (const [cs, entry] of Object.entries(entries)) {
            if (entry?.ts && (now - entry.ts) < ROUTE_CACHE_TTL) {
                routeCache.set(cs, entry.data);
                loaded++;
            }
        }
        if (loaded > 0) console.log(`[CACHE] Restored ${loaded} route entries from localStorage`);
    } catch (e) {
        console.warn('[CACHE] Failed to load route cache:', e);
    }
}

function saveRouteCache() {
    try {
        const entries = {};
        const now = Date.now();
        let count = 0;
        // Iterate newest-first (Map preserves insertion order, newest = last)
        const keys = [...routeCache.keys()].reverse();
        for (const cs of keys) {
            if (count >= ROUTE_CACHE_MAX) break;
            entries[cs] = { data: routeCache.get(cs), ts: now };
            count++;
        }
        localStorage.setItem(ROUTE_CACHE_KEY, JSON.stringify(entries));
    } catch (e) {
        console.warn('[CACHE] Failed to save route cache:', e);
    }
}


// ─── Persistent flight log (localStorage) ────────────────────────────────────
const LOG_KEY  = 'ps-log';
const LOG_MAX  = 200;
const DEDUP_MS = 3600_000; // 1 hour — don't re-log same callsign within this window

function getFlightLog() {
    try { return JSON.parse(localStorage.getItem(LOG_KEY)) || []; }
    catch { return []; }
}

function saveFlightLog(log) {
    try {
        while (log.length > LOG_MAX) log.shift(); // FIFO trim
        const json = JSON.stringify(log);
        if (json.length > 20_480) { // ~20 KB cap
            while (log.length > 0 && JSON.stringify(log).length > 20_480) log.shift();
        }
        localStorage.setItem(LOG_KEY, JSON.stringify(log));
    } catch (e) { console.warn('Flight log save failed:', e); }
}

function logFlight(flight, route) {
    if (!route || (!route.origin && !route.dest)) return; // need route data
    const cs = flight.callsign;
    if (!cs) return;

    const log = getFlightLog();
    const now = Date.now();

    // Dedup: skip if same callsign logged within 1 hour
    const recent = log.findLast(e => e.callsign === cs);
    if (recent && (now - new Date(recent.time).getTime()) < DEDUP_MS) return;

    log.push({
        callsign: cs,
        airline:  route.airlineIata || route.airlineName || '',
        origin:   route.origin || '',
        dest:     route.dest || '',
        alt:      flight.altFt || 0,
        time:     new Date().toISOString(),
    });
    saveFlightLog(log);
}

function renderFlightStats() {
    const statsEl = document.getElementById('flight-stats');
    if (!statsEl) return;
    const log = getFlightLog();
    if (!log.length) { statsEl.classList.add('fs-empty'); return; }
    statsEl.classList.remove('fs-empty');

    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const weekStart  = todayStart - 6 * 86400_000; // last 7 days including today

    let todayCount = 0, weekCount = 0;
    const airlineCounts = {};

    for (const e of log) {
        const t = new Date(e.time).getTime();
        if (t >= todayStart) todayCount++;
        if (t >= weekStart)  weekCount++;
        if (e.airline) airlineCounts[e.airline] = (airlineCounts[e.airline] || 0) + 1;
    }

    // Today / week counts
    document.getElementById('fs-today').textContent = `Today: ${todayCount}`;
    document.getElementById('fs-week').textContent  = `Week: ${weekCount}`;

    // Most seen airline
    const airlineEl = document.getElementById('fs-airline');
    const top = Object.entries(airlineCounts).sort((a, b) => b[1] - a[1])[0];
    if (top && top[1] > 1) {
        airlineEl.innerHTML = `Top: <span class="fs-airline-code">${top[0]}</span> \u00b7 ${top[1]} flights`;
    } else {
        airlineEl.textContent = '';
    }

    // Last spotted flight
    const lastEl = document.getElementById('fs-last');
    const last = log[log.length - 1];
    if (last) {
        const ago = relativeTime(new Date(last.time));
        const routeStr = (last.origin && last.dest)
            ? `${last.origin}\u2192${last.dest}` : (last.origin || last.dest || '');
        lastEl.innerHTML =
            `Last: <span class="fs-callsign">${last.callsign}</span>` +
            (routeStr ? ` <span class="fs-route">${routeStr}</span>` : '') +
            ` <span class="fs-ago">\u00b7 ${ago}</span>`;
    } else {
        lastEl.textContent = '';
    }
}

function relativeTime(date) {
    const s = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
    if (s < 60)    return 'just now';
    if (s < 3600)  return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
}

// ─── ICAO airline prefix → fallback airline info ──────────────────────────────
// ~200 entries: ICAO 3-letter code → 'Airline Name:IATA 2-letter code'
const ICAO_AIRLINES = {
    // ── North America ────────────────────────────────────────────────────────
    AAL:'American Airlines:AA', AAY:'Allegiant Air:G4', ACA:'Air Canada:AC',
    AMX:'Aeromexico:AM', ASA:'Alaska Airlines:AS', ASH:'Mesa Airlines:YV',
    DAL:'Delta Air Lines:DL', EDV:'Endeavor Air:9E', ENY:'Envoy Air:MQ',
    FFT:'Frontier Airlines:F9', GJS:'GoJet Airlines:G7',
    HAL:'Hawaiian Airlines:HA', JBU:'JetBlue:B6', JIA:'PSA Airlines:OH',
    NKS:'Spirit Airlines:NK', OPT:'Breeze Airways:MX', PDT:'Piedmont Airlines:PT',
    POE:'Porter Airlines:PD', QXE:'Horizon Air:QX', RPA:'Republic Airways:YX',
    SCX:'Sun Country Airlines:SY', SKW:'SkyWest Airlines:OO',
    SWA:'Southwest Airlines:WN', UAL:'United Airlines:UA',
    WJA:'WestJet:WS', WEN:'WestJet Encore:WR',

    // ── Latin America & Caribbean ────────────────────────────────────────────
    ARG:'Aerolineas Argentinas:AR', AVA:'Avianca:AV', AZU:'Azul:AD',
    BWA:'Caribbean Airlines:BW', CMP:'Copa Airlines:CM', GLO:'GOL:G3',
    LTM:'LATAM Airlines:LA', TAM:'LATAM Brasil:JJ', VCV:'Viva Aerobus:VB',
    VOI:'Volaris:Y4',

    // ── UK & Ireland ─────────────────────────────────────────────────────────
    BAW:'British Airways:BA', BEE:'Flybe:BE', EIN:'Aer Lingus:EI',
    EXS:'Jet2:LS', EZY:'easyJet:U2', EZS:'easyJet Switzerland:DS',
    LOG:'Loganair:LM', TOM:'TUI Airways:BY', VIR:'Virgin Atlantic:VS',

    // ── Western Europe ───────────────────────────────────────────────────────
    AFR:'Air France:AF', AZA:'ITA Airways:AZ', BEL:'Brussels Airlines:SN',
    CLH:'Lufthansa CityLine:CL', DLH:'Lufthansa:LH', EWG:'Eurowings:EW',
    HOP:'HOP!:A5', IBE:'Iberia:IB', IBK:'Norwegian Air Int:D8',
    KLM:'KLM:KL', LDA:'Lauda Europe:OE', LGL:'Luxair:LG',
    NAX:'Norwegian:DY', RYR:'Ryanair:FR', SWR:'Swiss:LX',
    TAP:'TAP Air Portugal:TP', TVF:'Transavia France:TO',
    TRA:'Transavia:HV', VLG:'Vueling:VY', VOE:'Volotea:V7',
    WZZ:'Wizz Air:W6',

    // ── Scandinavia & Baltics ────────────────────────────────────────────────
    BTI:'Air Baltic:BT', FIN:'Finnair:AY', ICE:'Icelandair:FI',
    SAS:'SAS:SK', WIF:'Wideroe:WF',

    // ── Central & Eastern Europe ─────────────────────────────────────────────
    AUA:'Austrian:OS', AEE:'Aegean Airlines:A3', CSA:'Czech Airlines:OK',
    LOT:'LOT Polish:LO', ROT:'TAROM:RO',

    // ── Germany charter/leisure ──────────────────────────────────────────────
    CFG:'Condor:DE', TUI:'TUIfly:X3',

    // ── Turkey & nearby ──────────────────────────────────────────────────────
    PGT:'Pegasus Airlines:PC', SXS:'SunExpress:XQ',
    THY:'Turkish Airlines:TK', CAI:'Corendon Airlines:XC',

    // ── Middle East ──────────────────────────────────────────────────────────
    ETD:'Etihad Airways:EY', FDB:'flydubai:FZ', FNS:'flynas:XY',
    GFA:'Gulf Air:GF', MEA:'Middle East Airlines:ME', OMA:'Oman Air:WY',
    QTR:'Qatar Airways:QR', RJA:'Royal Jordanian:RJ', SVA:'Saudia:SV',
    UAE:'Emirates:EK',

    // ── Africa ───────────────────────────────────────────────────────────────
    DAH:'Air Algerie:AH', ETH:'Ethiopian Airlines:ET', KQA:'Kenya Airways:KQ',
    MSR:'EgyptAir:MS', RAM:'Royal Air Maroc:AT', RWD:'RwandAir:WB',
    SAA:'South African Airways:SA',

    // ── South Asia ───────────────────────────────────────────────────────────
    AIC:'Air India:AI', IGO:'IndiGo:6E', LKA:'SriLankan Airlines:UL',
    SEJ:'SpiceJet:SG',

    // ── East Asia ────────────────────────────────────────────────────────────
    AAR:'Asiana Airlines:OZ', ANA:'All Nippon Airways:NH', APJ:'Peach Aviation:MM',
    CCA:'Air China:CA', CES:'China Eastern:MU', CHH:'Hainan Airlines:HU',
    CPA:'Cathay Pacific:CX', CSN:'China Southern:CZ', EVA:'EVA Air:BR',
    JAL:'Japan Airlines:JL', JJA:'Jeju Air:7C', JNA:'Jin Air:LJ',
    KAL:'Korean Air:KE', TWB:"T'way Air:TW",

    // ── Southeast Asia ───────────────────────────────────────────────────────
    AXM:'AirAsia:AK', CEB:'Cebu Pacific:5J', GIA:'Garuda Indonesia:GA',
    JSA:'Jetstar Asia:3K', LNI:'Lion Air:JT', MAS:'Malaysia Airlines:MH',
    SIA:'Singapore Airlines:SQ', SLK:'SilkAir:MI', TGW:'Scoot:TR',
    THA:'Thai Airways:TG', VJC:'VietJet Air:VJ', HVN:'Vietnam Airlines:VN',

    // ── Oceania ──────────────────────────────────────────────────────────────
    ANZ:'Air New Zealand:NZ', JST:'Jetstar:JQ', QFA:'Qantas:QF',
    VOZ:'Virgin Australia:VA',

    // ── Cargo ────────────────────────────────────────────────────────────────
    ABW:'AirBridgeCargo:RU', ATN:'Air Transport Int:8C', BOX:'AeroLogic:3S',
    CLX:'Cargolux:CV', CKS:'Kalitta Air:K4', DHL:'DHL Aviation:ES',
    FDX:'FedEx Express:FX', GTI:'Atlas Air:5Y', MPH:'Martinair:MP',
    PAC:'Polar Air Cargo:PO', AZG:'Silk Way Airlines:ZP',
    SQC:'Singapore Airlines Cargo:SQ', UPS:'UPS Airlines:5X',

    // ── Russia & CIS (still occasionally seen on ADS-B) ─────────────────────
    AFL:'Aeroflot:SU', SAW:'S7 Airlines:S7',
};
function airlineFromCallsign(cs) {
    const prefix = cs.replace(/[0-9]/g, '').substring(0, 3).toUpperCase();
    const entry = ICAO_AIRLINES[prefix];
    if (!entry) return null;
    const [name, iata] = entry.split(':');
    return { airlineName: name, airlineIata: iata };
}

// ─── Unusual traffic classification ──────────────────────────────────────────
// Returns { tag, tier } or null.  tier = 'rare' | 'uncommon'
const MIL_PREFIXES = new Set([
    'RRR',                          // RAF
    'RFR',                          // French Air Force
    'GAF',                          // German Air Force
    'IAM',                          // Italian Air Force
    'BAF',                          // Belgian Air Force
    'NAF',                          // Netherlands Air Force
    'FAF',                          // Finnish Air Force
    'SVF',                          // Swedish Air Force
    'DAF',                          // Danish Air Force
    'NOR',                          // Royal Norwegian Air Force
    'HUF',                          // Hungarian Air Force
    'PLF',                          // Polish Air Force
    'CFE',                          // Canadian Forces
    'RCH',                          // USAF (Reach callsign)
    'AIO',                          // USAF (Aero Intel)
]);
const MIL_CALLSIGN_STARTS = [
    'DUKE', 'ASCOT', 'REACH', 'EVAC', 'NAVY', 'CASA',
    'CANFO', 'JAKE', 'TOPCAT', 'VIPER', 'RAFR',
    'TALLY', 'CHAOS', 'DEMON', 'MOOSE',
];
const MIL_TYPES = new Set([
    'C17','C130','C30J','C30H','A400','A40M',       // transports
    'KC10','KC46','KC35','A332','A333',              // tankers (military variants)
    'EUFI','F16','F15E','F15','F18','FA18',          // fighters
    'F35','F22','HAWK','TUCA','GR4','TORN',
    'P8','P8A','P3','E3CF','E3TF','E6B','E8',       // surveillance / AWACS
    'U2','GLHK','RQ4','MQ9','MQ1',                  // high-alt / UAV
    'RC35','GLEX','BE20','C12',                      // ISR
    'V22','CH47','CH53','UH60','AH64','MRH9',       // military rotary
    'A109','LYNX','WILD','MRLX','NH90',
    'C5','C5M','C2','C27J','CN35',                   // heavy transports
]);
const RARE_TYPES = new Set([
    'A124','A225','BLCF','BLNG','CONC',              // AN-124, AN-225, Beluga XL/ST, Concorde
    'B52','B1','B2','A10',                           // bombers (rare over UK)
]);
const HELI_TYPES = new Set([
    'H125','H130','H135','H145','H155','H160','H175','H215','H225',
    'EC20','EC25','EC30','EC35','EC45','EC55','EC75',
    'S61','S70','S76','S92',
    'AW09','AW13','AW16','AW18','AW10','AW19',
    'B06','B07','B22','B47','B05','B06T',
    'R22','R44','R66',
    'BK17','B412','B429','B505','B407','B206','B212',
    'AS50','AS55','AS65','AS32','AS33','AS35',
    'MD52','MD60','MD90','EXPL','LAMA','HUCO',
    'A109','A119','A139','A149','A169','A189',
    'K32','KMAX','R900','CABR','DJIN',
    'G2CA','REVO','GUIM','EXEC',
]);

function classifyFlight(f, route) {
    const cs   = (f.callsign || '').toUpperCase();
    const type = (f.type || '').toUpperCase();

    // ── RARE: military callsign prefix ──────────────────────────────────────
    const prefix3 = cs.replace(/[0-9]/g, '').substring(0, 3);
    if (MIL_PREFIXES.has(prefix3)) return { tag: 'MILITARY', tier: 'rare' };

    // ── RARE: military callsign start words ─────────────────────────────────
    for (const s of MIL_CALLSIGN_STARTS) {
        if (cs.startsWith(s)) return { tag: 'MILITARY', tier: 'rare' };
    }

    // ── RARE: military aircraft types ───────────────────────────────────────
    if (MIL_TYPES.has(type)) return { tag: 'MILITARY', tier: 'rare' };

    // ── RARE: genuinely rare civilian types ─────────────────────────────────
    if (RARE_TYPES.has(type)) return { tag: 'RARE TYPE', tier: 'rare' };

    // ── RARE: extreme altitude ──────────────────────────────────────────────
    if (f.altFt > 50000) return { tag: 'HIGH ALT', tier: 'rare' };

    // ── UNCOMMON: helicopters ───────────────────────────────────────────────
    if (HELI_TYPES.has(type)) return { tag: 'HELICOPTER', tier: 'uncommon' };

    // ── UNCOMMON: first-time airline sighting ───────────────────────────────
    if (route?.airlineName && typeof flightLog !== 'undefined' && flightLog.length > 0) {
        const seen = flightLog.some(e => e.airline === route.airlineName || e.airline === route.airlineIata);
        if (!seen) return { tag: 'FIRST SIGHTING', tier: 'uncommon' };
    }

    return null;
}

// ─── Unit helpers ─────────────────────────────────────────────────────────────
const mToFt    = m  => Math.round(m * 3.28084);
const mpsToMph   = v => Math.round(v * 2.23694);
const mpsToKph   = v => Math.round(v * 3.6);
const mpsToKnots = v => Math.round(v * 1.94384);

function speed(mps) { return mpsToKnots(mps); }  // aviation always uses knots
function distFmt(mi) { return mi.toFixed(1); }  // aviation always uses statute miles
const distLbl = () => 'mi';
const spdLbl  = () => 'kts';
const tempLbl = () => UNITS === 'imperial' ? '°F'  : '°C';

// ─── Maths ────────────────────────────────────────────────────────────────────
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
function toCompass16(deg) {
    const dirs = ['N','NNE','NE','ENE','E','ESE','SE','SSE','S','SSW','SW','WSW','W','WNW','NW','NNW'];
    return dirs[Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
}
const kmhToKnots = v => Math.round(v / 1.852);
// Route plausibility: is aircraft roughly "on" the claimed route?
// Uses triangle inequality — sum of distances to endpoints vs direct route distance.
// If detour ratio > 1.5, route data is likely stale/wrong (airlines reuse callsigns).
function isRoutePlausible(route, acLat, acLon) {
    if (!route?.originLat || !route?.destLat) return true; // can't validate → show it
    const oLat = parseFloat(route.originLat), oLon = parseFloat(route.originLon);
    const dLat = parseFloat(route.destLat),   dLon = parseFloat(route.destLon);
    if (isNaN(oLat) || isNaN(dLat)) return true;
    const routeDist  = haversine(oLat, oLon, dLat, dLon);
    if (routeDist < 50) return true; // very short route, skip check
    const toOrig = haversine(acLat, acLon, oLat, oLon);
    const toDest = haversine(acLat, acLon, dLat, dLon);
    const detour = (toOrig + toDest) / routeDist;
    if (detour > 1.5) {
        console.warn(`[ROUTE] Implausible: ${route.origin}→${route.dest} detour=${detour.toFixed(2)}x (ac@${acLat.toFixed(2)},${acLon.toFixed(2)})`);
        return false;
    }
    return true;
}

function bearing(lat1, lon1, lat2, lon2) {
    const toRad = d => d * Math.PI / 180;
    const dLon = toRad(lon2 - lon1);
    const y = Math.sin(dLon) * Math.cos(toRad(lat2));
    const x = Math.cos(toRad(lat1)) * Math.sin(toRad(lat2))
            - Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(dLon);
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}
function boundingBox(lat, lon, r) {
    const dLat = r / 69;
    const dLon = r / (69 * Math.cos(lat * Math.PI / 180));
    return { lamin: lat-dLat, lomin: lon-dLon, lamax: lat+dLat, lomax: lon+dLon };
}

// ─── Bezier helpers ───────────────────────────────────────────────────────────
function bezierPt(t, x0, y0, cx, cy, x1, y1) {
    const u = 1 - t;
    return {
        x: u*u*x0 + 2*u*t*cx + t*t*x1,
        y: u*u*y0 + 2*u*t*cy + t*t*y1,
    };
}
function bezierTangent(t, x0, y0, cx, cy, x1, y1) {
    return {
        dx: 2*(1-t)*(cx-x0) + 2*t*(x1-cx),
        dy: 2*(1-t)*(cy-y0) + 2*t*(y1-cy),
    };
}
// Cubic bezier: B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
function cubicPt(t, p0, p1, p2, p3) {
    const u = 1 - t;
    return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
}
function cubicDeriv(t, p0, p1, p2, p3) {
    const u = 1 - t;
    return 3*u*u*(p1-p0) + 6*u*t*(p2-p1) + 3*t*t*(p3-p2);
}

// ─── Time / date ──────────────────────────────────────────────────────────────
function fmtTime(d) {
    let h = d.getHours(), m = String(d.getMinutes()).padStart(2,'0');
    const ap = h >= 12 ? 'PM' : 'AM';
    h = h % 12 || 12;
    return `${h}:${m} ${ap}`;
}
function fmtDate(d) {
    return d.toLocaleDateString('en-GB', { weekday:'short', day:'numeric', month:'short' });
}
function fmtDayShort(iso, index) {
    if (index === 0) return 'TODAY';
    if (index === 1) return 'TOMORROW';
    return new Date(iso + 'T12:00:00').toLocaleDateString('en-GB', { weekday:'short' }).toUpperCase();
}

// ─── Clock tick ───────────────────────────────────────────────────────────────
let _lastStatsMin = -1;
function tickClock() {
    const now = new Date();
    const t = fmtTime(now), d = fmtDate(now);
    document.getElementById('hd-time').textContent  = t;
    document.getElementById('hd-date').textContent  = d;
    document.getElementById('big-time').textContent = t;

    // Refresh flight stats every minute (updates relative times + counts)
    const curMin = now.getMinutes();
    if (curMin !== _lastStatsMin) {
        _lastStatsMin = curMin;
        renderFlightStats();
    }

    // Refresh golden hour state
    updateGoldenHour();
}

// ─── Animated number counter ──────────────────────────────────────────────────
function animateVal(el, targetStr, duration = 700) {
    const rawTarget = parseFloat(targetStr.replace(/[^0-9.]/g, ''));
    if (isNaN(rawTarget)) { el.textContent = targetStr; return; }
    const rawStart  = parseFloat(el.textContent.replace(/[^0-9.]/g, '')) || 0;
    if (rawStart === rawTarget) { el.textContent = targetStr; return; }
    const isFloat   = targetStr.includes('.');
    const t0        = performance.now();
    const diff      = rawTarget - rawStart;
    function step(now) {
        const p   = Math.min((now - t0) / duration, 1);
        const ep  = 1 - Math.pow(1 - p, 3); // ease-out cubic
        const val = rawStart + diff * ep;
        el.textContent = isFloat ? val.toFixed(1) : Math.round(val).toLocaleString();
        if (p < 1) requestAnimationFrame(step);
        else       el.textContent = targetStr; // snap to exact final value
    }
    requestAnimationFrame(step);
}

// ─── Fetch flights (adsb.fi via Cloudflare Worker proxy) ─────────────────────
const ADSB_PROXY = 'https://plane-tracker-proxy.plane-tracker-proxy.workers.dev';
const NM_TO_MI   = 1.15078;

async function fetchFlights() {
    const distNm = Math.ceil(BOX_RADIUS_MILES / NM_TO_MI);
    const url = `${ADSB_PROXY}/lat/${userLat.toFixed(4)}/lon/${userLon.toFixed(4)}/dist/${distNm}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Flight API ${res.status}`);
    const json = await res.json();
    const ac = json.ac || json.aircraft || [];
    if (!ac.length) return [];
    return ac
        .filter(a => a.lat != null && a.lon != null && a.alt_baro !== 'ground' && typeof a.alt_baro === 'number')
        .map(a => {
            const distMi = a.dst != null ? a.dst * NM_TO_MI : haversine(userLat, userLon, a.lat, a.lon);
            return {
                icao24:   a.hex,
                callsign: (a.flight || '').trim() || a.hex.toUpperCase(),
                country:  '',
                lat: a.lat, lon: a.lon,
                altFt:    a.alt_baro || a.alt_geom || 0,
                spd:      Math.round(a.gs || 0),
                dir:      toCardinal(bearing(userLat, userLon, a.lat, a.lon)),
                vrateFpm: Math.round(a.baro_rate || 0),
                navAlt:   typeof a.nav_altitude_mcp === 'number' ? a.nav_altitude_mcp : null,
                navModes: Array.isArray(a.nav_modes) ? a.nav_modes : [],
                distMi,
                type:     a.t || '',
                reg:      a.r || '',
            };
        })
        .filter(f => f.altFt >= MIN_ALT_FT)
        .sort((a, b) => a.distMi - b.distMi)
        .slice(0, 5);
}

// ─── ICAO airport code → IATA code + city name ──────────────────────────────
// Covers major airports where adsbdb/hexdb return ICAO codes without IATA/city.
// Format: 'IATA:City Name'
const ICAO_AIRPORTS = {
    // UK & Ireland
    EGLL:'LHR:London',EGKK:'LGW:London Gatwick',EGSS:'STN:London Stansted',
    EGLC:'LCY:London City',EGCC:'MAN:Manchester',EGBB:'BHX:Birmingham',
    EGGD:'BRS:Bristol',EGNX:'EMA:East Midlands',EGPH:'EDI:Edinburgh',
    EGGW:'LTN:Luton',EGPF:'GLA:Glasgow',EGAA:'BFS:Belfast',EGAC:'BHD:Belfast City',
    EGNM:'LBA:Leeds Bradford',EGGP:'LPL:Liverpool',EGHI:'SOU:Southampton',
    EGNT:'NCL:Newcastle',EGPD:'ABZ:Aberdeen',EIDW:'DUB:Dublin',EICK:'ORK:Cork',
    EINN:'SNN:Shannon',
    // Europe
    LFPG:'CDG:Paris',LFPO:'ORY:Paris Orly',EHAM:'AMS:Amsterdam',
    EDDF:'FRA:Frankfurt',EDDM:'MUC:Munich',EDDB:'BER:Berlin',
    LEMD:'MAD:Madrid',LEBL:'BCN:Barcelona',LPPT:'LIS:Lisbon',
    LIRF:'FCO:Rome',LIMC:'MXP:Milan Malpensa',LSZH:'ZRH:Zurich',
    LOWW:'VIE:Vienna',EBBR:'BRU:Brussels',EKCH:'CPH:Copenhagen',
    ENGM:'OSL:Oslo',ESSA:'ARN:Stockholm',EFHK:'HEL:Helsinki',
    EPWA:'WAW:Warsaw',LKPR:'PRG:Prague',LHBP:'BUD:Budapest',
    LGAV:'ATH:Athens',LTFM:'IST:Istanbul',LTAI:'AYT:Antalya',
    LROP:'OTP:Bucharest',LDZA:'ZAG:Zagreb',LYBE:'BEG:Belgrade',
    LEAL:'ALC:Alicante',LEMG:'AGP:Malaga',LPFR:'FAO:Faro',
    LGIR:'HER:Heraklion',LGKO:'KGS:Kos',LGRP:'RHO:Rhodes',
    LMML:'MLA:Malta',BIKF:'KEF:Reykjavik',LICJ:'PMO:Palermo',
    LFMN:'NCE:Nice',LFLL:'LYS:Lyon',LSGG:'GVA:Geneva',
    EDDL:'DUS:Dusseldorf',EDDH:'HAM:Hamburg',EDDK:'CGN:Cologne',
    EDDS:'STR:Stuttgart',EDDN:'NUE:Nuremberg',EDDP:'LEJ:Leipzig',
    EHRD:'RTM:Rotterdam',LIPE:'BLQ:Bologna',LIPZ:'VCE:Venice',
    LIRA:'CIA:Rome Ciampino',GCFV:'FUE:Fuerteventura',GCTS:'TFS:Tenerife South',
    GCLP:'LPA:Gran Canaria',GCXO:'TFN:Tenerife North',GCRR:'ACE:Lanzarote',
    LEPA:'PMI:Palma de Mallorca',LEBB:'BIO:Bilbao',
    EDDT:'TXL:Berlin Tegel',EDDW:'BRE:Bremen',
    // Middle East
    OMDB:'DXB:Dubai',OMDW:'DWC:Dubai World Central',OMAA:'AUH:Abu Dhabi',
    OBBI:'BAH:Bahrain',OTHH:'DOH:Doha',OEJN:'JED:Jeddah',OERK:'RUH:Riyadh',
    OOMS:'MCT:Muscat',OKBK:'KWI:Kuwait',OLBA:'BEY:Beirut',
    LLBG:'TLV:Tel Aviv',OJAM:'AMM:Amman',
    // North America
    KJFK:'JFK:New York',KLAX:'LAX:Los Angeles',KORD:'ORD:Chicago',
    KATL:'ATL:Atlanta',KDFW:'DFW:Dallas',KDEN:'DEN:Denver',
    KSFO:'SFO:San Francisco',KSEA:'SEA:Seattle',KMIA:'MIA:Miami',
    KEWR:'EWR:Newark',KBOS:'BOS:Boston',KIAD:'IAD:Washington Dulles',
    KDCA:'DCA:Washington Reagan',KPHL:'PHL:Philadelphia',KMSP:'MSP:Minneapolis',
    KDTW:'DTW:Detroit',KFLL:'FLL:Fort Lauderdale',KMCO:'MCO:Orlando',
    KTPA:'TPA:Tampa',KLAS:'LAS:Las Vegas',KPHX:'PHX:Phoenix',
    KSLC:'SLC:Salt Lake City',KSAN:'SAN:San Diego',KPDX:'PDX:Portland',
    KCLT:'CLT:Charlotte',KBWI:'BWI:Baltimore',KRDU:'RDU:Raleigh',
    KSTL:'STL:St Louis',KMKE:'MKE:Milwaukee',KPIT:'PIT:Pittsburgh',
    KCLE:'CLE:Cleveland',KAUS:'AUS:Austin',KSAT:'SAT:San Antonio',
    KHOU:'HOU:Houston Hobby',KIAH:'IAH:Houston',KBNA:'BNA:Nashville',
    CYYZ:'YYZ:Toronto',CYUL:'YUL:Montreal',CYVR:'YVR:Vancouver',
    CYOW:'YOW:Ottawa',CYYC:'YYC:Calgary',CYEG:'YEG:Edmonton',
    CYHA:'YHZ:Halifax',MMMX:'MEX:Mexico City',MMUN:'CUN:Cancun',
    // Asia-Pacific
    VHHH:'HKG:Hong Kong',WSSS:'SIN:Singapore',VTBS:'BKK:Bangkok',
    RPLL:'MNL:Manila',WIII:'CGK:Jakarta',WMKK:'KUL:Kuala Lumpur',
    RJTT:'HND:Tokyo Haneda',RJAA:'NRT:Tokyo Narita',RKSI:'ICN:Seoul Incheon',
    ZBAA:'PEK:Beijing',ZSPD:'PVG:Shanghai',ZGGG:'CAN:Guangzhou',
    VABB:'BOM:Mumbai',VIDP:'DEL:Delhi',VOBL:'BLR:Bangalore',
    YSSY:'SYD:Sydney',YMML:'MEL:Melbourne',YBBN:'BNE:Brisbane',
    NZAA:'AKL:Auckland',NZCH:'CHC:Christchurch',
    // Africa
    FACT:'CPT:Cape Town',FAOR:'JNB:Johannesburg',HECA:'CAI:Cairo',
    GMMN:'CMN:Casablanca',DNMM:'LOS:Lagos',HKJK:'NBO:Nairobi',
    HAAB:'ADD:Addis Ababa',DTTA:'TUN:Tunis',DAAG:'ALG:Algiers',
    // South America
    SBGR:'GRU:Sao Paulo',SCEL:'SCL:Santiago',SAEZ:'EZE:Buenos Aires',
    SKBO:'BOG:Bogota',SPJC:'LIM:Lima',SEQM:'UIO:Quito',
    // Frankfurt Hahn (from the screenshot)
    EDFH:'HHN:Frankfurt Hahn',
    // Greenville-Spartanburg
    KGSP:'GSP:Greer',
};

function normalizeRoute(route) {
    if (!route) return route;
    for (const key of ['origin', 'dest']) {
        const code = route[key];
        if (!code || code.length !== 4) continue; // only process 4-char ICAO codes
        const entry = ICAO_AIRPORTS[code];
        if (entry) {
            const [iata, city] = entry.split(':');
            route[key] = iata;
            const nameKey = key + 'Name';
            if (!route[nameKey]) route[nameKey] = city;
        }
    }
    return route;
}

// ─── Fetch route + airline (adsbdb.com → hexdb.io fallback) ──────────────────
async function fetchRoute(callsign) {
    if (routeCache.has(callsign)) return routeCache.get(callsign);
    const airlineFb = airlineFromCallsign(callsign) || null;

    // --- 1. Try adsbdb.com first ---
    try {
        const res = await fetch(`https://api.adsbdb.com/v0/callsign/${encodeURIComponent(callsign)}`);
        if (res.ok) {
            const json = await res.json();
            const fr = json?.response?.flightroute;
            if (fr) {
                const route = {
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
                normalizeRoute(route);
                routeCache.set(callsign, route);
                saveRouteCache();
                return route;
            }
        }
    } catch (e) {
        console.warn('[ROUTE] adsbdb failed for', callsign, e.message);
    }

    // --- 2. Fallback: try hexdb.io (ICAO airport codes) ---
    try {
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 5000);
        const hres = await fetch(
            `https://hexdb.io/api/v1/route/icao/${encodeURIComponent(callsign)}`,
            { signal: ctrl.signal }
        );
        clearTimeout(timer);
        if (hres.ok) {
            const hj = await hres.json();
            // hj.route = "EGLL-KIAD" (ICAO codes separated by hyphen)
            if (hj.route && hj.route.includes('-')) {
                const [orig, dest] = hj.route.split('-');
                if (orig && dest) {
                    const route = {
                        origin: orig, dest: dest,
                        originName: null, destName: null,
                        originLat: null, originLon: null,
                        destLat: null, destLon: null,
                        airlineName: airlineFb?.airlineName || null,
                        airlineIata: airlineFb?.airlineIata || null,
                    };
                    normalizeRoute(route);
                    console.log(`[ROUTE] hexdb.io resolved ${callsign} -> ${route.origin}-${route.dest}`);
                    routeCache.set(callsign, route);
                    saveRouteCache();
                    return route;
                }
            }
        }
    } catch (e) {
        if (e.name !== 'AbortError') console.warn('[ROUTE] hexdb failed for', callsign, e.message);
    }

    // --- 3. Fall back to airline-only from ICAO table ---
    routeCache.set(callsign, airlineFb);
    saveRouteCache();
    return airlineFb;
}

// ─── Fetch aircraft type (adsbdb.com) ─────────────────────────────────────────
async function fetchAircraft(icao24) {
    if (aircraftCache.has(icao24)) return aircraftCache.get(icao24);
    try {
        const res = await fetch(`https://api.adsbdb.com/v0/aircraft/${encodeURIComponent(icao24)}`);
        if (!res.ok) { aircraftCache.set(icao24, null); return null; }
        const json = await res.json();
        const ac = json?.response?.aircraft;
        if (!ac) { aircraftCache.set(icao24, null); return null; }
        const info = { type: ac.type || ac.icao_type || null, manufacturer: ac.manufacturer || null };
        aircraftCache.set(icao24, info);
        return info;
    } catch {
        aircraftCache.set(icao24, null);
        return null;
    }
}

// ─── Fetch weather (Open-Meteo) ───────────────────────────────────────────────
async function fetchWeather() {
    const url = `https://api.open-meteo.com/v1/forecast`
              + `?latitude=${userLat.toFixed(4)}&longitude=${userLon.toFixed(4)}`
              + `&current=temperature_2m,relative_humidity_2m,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m`
              + `&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset`
              + `&forecast_days=3&timezone=auto`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Open-Meteo ${res.status}`);
    return res.json();
}

// ─── Render: airline name (with scroll if too long) ───────────────────────────
function setAirlineName(name) {
    const el   = document.getElementById('airline-name');
    const wrap = document.getElementById('airline-name-wrap');
    el.classList.remove('scrolling');
    el.style.removeProperty('--marquee-dist');
    el.textContent = name || '';
    requestAnimationFrame(() => {
        const overflow = el.scrollWidth - wrap.clientWidth;
        if (overflow > 8) {
            el.style.setProperty('--marquee-dist', `-${overflow + 24}px`);
            el.classList.add('scrolling');
        }
    });
}

// ─── Render: SVG bezier arc ───────────────────────────────────────────────────
function renderArc(f, route) {
    const svg = document.getElementById('route-arc');
    const container = document.getElementById('arc-container');
    const W = Math.max(container.offsetWidth  || 300, 100);
    const H = Math.max(container.offsetHeight || 120, 60);
    svg.setAttribute('viewBox', `0 0 ${W} ${H}`);

    // ── Layout constants ─────────────────────────────────────────────────────
    const padX  = Math.round(W * 0.06);
    const xOrig = padX;
    const xDest = W - padX;
    const yGnd  = H - 14; // ground level
    const planeSize = Math.max(16, Math.round(H * 0.20));

    // ── Phase fractions (by route distance) ──────────────────────────────────
    const CLIMB_F   = 0.18;  // 18% = climb
    const DESCENT_F = 0.22;  // 22% = descent (longer = gentler visual slope)
    const CRUISE_F  = 1 - CLIMB_F - DESCENT_F; // 60% = cruise

    const xTOC = xOrig + (xDest - xOrig) * CLIMB_F;
    const xTOD = xDest - (xDest - xOrig) * DESCENT_F;

    // ── Cruise altitude: computed so descent slope ≤ 3:1 (h:w) ──────────────
    const descentW = xDest - xTOD;
    const maxDropH = descentW * 2.8;
    const yCrz = Math.max(Math.round(H * 0.08), Math.round(yGnd - maxDropH));

    // ── Cubic bezier control points ──────────────────────────────────────────
    // Climb: start near-vertical, arrive horizontal at TOC
    const ccp1x = xOrig + (xTOC - xOrig) * 0.1, ccp1y = yGnd;
    const ccp2x = xTOC - (xTOC - xOrig) * 0.1,  ccp2y = yCrz;
    // Descent: depart horizontal, steepen, then gentle flare to ground
    const dcp1x = xTOD + (xDest - xTOD) * 0.1, dcp1y = yCrz;
    const dcp2x = xDest - (xDest - xTOD) * 0.1, dcp2y = yGnd - (yGnd - yCrz) * 0.15;

    const pathD = [
        `M ${xOrig},${yGnd}`,
        `C ${ccp1x},${ccp1y} ${ccp2x},${ccp2y} ${xTOC},${yCrz}`,
        `L ${xTOD},${yCrz}`,
        `C ${dcp1x},${dcp1y} ${dcp2x},${dcp2y} ${xDest},${yGnd}`,
    ].join(' ');

    // ── Progress ─────────────────────────────────────────────────────────────
    const hasData = !!(route?.originLat && route?.destLat);
    let pct = 50, totalMi = 0;
    if (hasData) {
        totalMi = haversine(route.originLat, route.originLon, route.destLat, route.destLon);
        if (totalMi > 10) {
            const flown = haversine(route.originLat, route.originLon, f.lat, f.lon);
            pct = Math.max(2, Math.min(98, (flown / totalMi) * 100));
        }
    }

    // ── Telemetry-based phase detection ─────────────────────────────────────
    // Real-world climb/descent distances are roughly constant regardless of
    // total route length (~150mi climb to cruise, ~100mi descent to landing).
    // Use these + actual vertical rate to determine the flight's true phase,
    // then remap distance % onto the visual arc segments.
    const CLIMB_DIST_MI   = 150;
    const DESCENT_DIST_MI = 100;
    const dp = pct / 100; // distance progress 0–1

    // Real-world phase boundaries (as fraction of total route)
    const realClimbEnd     = totalMi > 50 ? Math.min(0.35, CLIMB_DIST_MI / totalMi) : 0.18;
    const realDescentStart = totalMi > 50 ? Math.max(0.65, 1 - DESCENT_DIST_MI / totalMi) : 0.78;

    // Detect actual phase from aircraft telemetry
    // Thresholds align with the climb-rate display (±200 fpm)
    let phase;
    if (f.vrateFpm > 200)                                       phase = 'climb';
    else if (f.vrateFpm < -200)                                 phase = 'descent';
    else if (f.navModes && f.navModes.includes('approach'))     phase = 'descent';
    else if (f.navAlt != null && f.altFt > 15000
             && f.navAlt < f.altFt - 3000
             && f.vrateFpm < -100)                              phase = 'descent';
    else if (f.altFt > 10000)                                   phase = 'cruise';
    else if (dp < 0.5)                                          phase = 'climb';
    else                                                        phase = 'descent';

    // Remap distance % → visual arc position (tn 0–1)
    // Maps real-world phase boundaries to the visual CLIMB_F / CRUISE_F / DESCENT_F
    let tn;
    if (phase === 'climb') {
        const t = Math.min(dp / realClimbEnd, 1);
        tn = t * CLIMB_F;
    } else if (phase === 'descent') {
        const t = realDescentStart < 1
            ? Math.max(0, Math.min(1, (dp - realDescentStart) / (1 - realDescentStart)))
            : 1;
        tn = CLIMB_F + CRUISE_F + t * DESCENT_F;
    } else {
        const span = realDescentStart - realClimbEnd;
        const t = span > 0 ? Math.max(0, Math.min(1, (dp - realClimbEnd) / span)) : 0.5;
        tn = CLIMB_F + t * CRUISE_F;
    }

    // ── Plane position & angle ────────────────────────────────────────────────
    let px, py, angle;
    if (tn <= CLIMB_F) {
        const t = CLIMB_F > 0 ? tn / CLIMB_F : 0;
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

    // Clip flown portion by x (left of plane)
    const clipW = Math.max(px + 2, 0);

    svg.innerHTML = `
        <defs>
            <linearGradient id="arc-grad" x1="0%" y1="0%" x2="100%" y2="0%">
                <stop offset="0%"   stop-color="#e8e0d0" stop-opacity="1"/>
                <stop offset="${CLIMB_F * 100}%"   stop-color="#d4a847" stop-opacity="0.9"/>
                <stop offset="${(CLIMB_F + CRUISE_F) * 100}%" stop-color="#d4a847" stop-opacity="0.9"/>
                <stop offset="100%" stop-color="#e8e0d0" stop-opacity="1"/>
            </linearGradient>
            <clipPath id="flown-clip">
                <rect x="0" y="0" width="${clipW}" height="${H + 10}"/>
            </clipPath>
            <filter id="glow-filter">
                <feGaussianBlur stdDeviation="2.5" result="blur"/>
                <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
        </defs>

        <!-- Ground line -->
        <line x1="${xOrig}" y1="${yGnd + 6}" x2="${xDest}" y2="${yGnd + 6}"
              stroke="rgba(255,255,255,0.12)" stroke-width="1"/>

        <!-- Full route: dim dashed -->
        <path d="${pathD}" stroke="rgba(255,255,255,0.12)" stroke-width="1.5"
              stroke-dasharray="6,10" fill="none"/>

        <!-- Flown portion: bright gradient with glow -->
        <path d="${pathD}" stroke="url(#arc-grad)" stroke-width="2.5"
              fill="none" clip-path="url(#flown-clip)" filter="url(#glow-filter)"/>

        <!-- Cruise altitude guide: faint horizontal line -->
        <line x1="${xTOC}" y1="${yCrz}" x2="${xTOD}" y2="${yCrz}"
              stroke="rgba(212,168,71,0.20)" stroke-width="1" stroke-dasharray="2,6"/>

        <!-- Origin dot -->
        <circle cx="${xOrig}" cy="${yGnd}" r="5" fill="#d4a847" opacity="0.25">
            <animate attributeName="r" values="5;9;5" dur="2.4s" repeatCount="indefinite"/>
            <animate attributeName="opacity" values="0.25;0.05;0.25" dur="2.4s" repeatCount="indefinite"/>
        </circle>
        <circle cx="${xOrig}" cy="${yGnd}" r="3.5" fill="#d4a847"/>

        <!-- Destination dot -->
        <circle cx="${xDest}" cy="${yGnd}" r="3.5" fill="#e8e0d0"
                opacity="${hasData ? 0.7 : 0.3}"/>

        <!-- Plane marker at current position (matches header ✈) -->
        <text x="${px}" y="${py}"
              transform="rotate(${angle}, ${px}, ${py})"
              text-anchor="middle" dominant-baseline="central"
              font-size="${planeSize}" fill="var(--text-primary)"
              filter="url(#glow-filter)"
              style="user-select:none">✈</text>
    `;
}

// ─── Render: wrap text in split-flap cells ───────────────────────────────────
function flapText(str, lg = false) {
    const cls = lg ? 'flap flap-lg' : 'flap';
    return [...str].map(ch => ch === ' '
        ? '<span style="width:6px;display:inline-block"></span>'
        : `<span class="${cls}">${ch}</span>`
    ).join('');
}

// ─── Render: animated split-flap cycling ─────────────────────────────────────
const FLAP_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

// Solari split-flap animation — mechanical drum simulation
// Real Solari boards have a drum with characters in fixed order. All drums
// start spinning simultaneously, each cycling sequentially through the
// character set. A drum stops when it reaches its target character, so
// positions needing fewer flips settle first (natural stagger).
const FLAP_ORDER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789·?';
const FLAP_TICK  = 180;  // ms per flip — slightly faster than real Solari (~200ms)
const FLAP_MIN   = 6;    // minimum flips so every position visibly animates

function flapAnimate(element, targetText, lg = false) {
    const cls = lg ? 'flap flap-lg' : 'flap';
    const chars = [...targetText];
    const len = chars.length;

    // Per-position state: current index in FLAP_ORDER, target index, remaining flips
    const spans    = [];
    const curIdx   = [];
    const tgtIdx   = [];
    const remain   = [];
    const settled  = [];

    element.innerHTML = '';

    for (let i = 0; i < len; i++) {
        const ch = chars[i];
        if (ch === ' ') {
            const spacer = document.createElement('span');
            spacer.style.cssText = 'width:6px;display:inline-block';
            element.appendChild(spacer);
            spans.push(null);
            curIdx.push(0); tgtIdx.push(0); remain.push(0); settled.push(true);
            continue;
        }

        const span = document.createElement('span');
        span.className = cls;

        const ti = FLAP_ORDER.indexOf(ch.toUpperCase());
        const target = ti >= 0 ? ti : 0;

        // Each position does at least FLAP_MIN flips, plus 0-14 extra for variety
        const totalFlips = FLAP_MIN + Math.floor(Math.random() * 15);
        const start = ((target - totalFlips) % FLAP_ORDER.length + FLAP_ORDER.length) % FLAP_ORDER.length;

        span.textContent = FLAP_ORDER[start];
        element.appendChild(span);

        spans.push(span);
        curIdx.push(start);
        tgtIdx.push(target);
        remain.push(totalFlips);
        settled.push(false);
    }

    // All drums flip simultaneously; each stops when it hits its target
    const ticker = setInterval(() => {
        let allDone = true;
        for (let i = 0; i < len; i++) {
            if (settled[i] || !spans[i]) continue;
            curIdx[i] = (curIdx[i] + 1) % FLAP_ORDER.length;
            remain[i]--;
            spans[i].textContent = FLAP_ORDER[curIdx[i]];
            spans[i].classList.remove('ticking');
            void spans[i].offsetWidth;           // reflow to restart animation
            spans[i].classList.add('ticking');

            if (remain[i] <= 0) {
                settled[i] = true;
                spans[i].textContent = chars[i]; // snap to exact target
                spans[i].classList.remove('ticking');
                spans[i].classList.add('settling');
                spans[i].addEventListener('animationend', () => {
                    spans[i].classList.remove('settling');
                }, { once: true });
            } else {
                allDone = false;
            }
        }
        if (allDone) clearInterval(ticker);
    }, FLAP_TICK);
}

// ─── Render: callsign with flap cells ────────────────────────────────────────
function coloredCallsign(cs) {
    return flapText(cs);
}

// ─── Render: full flight card ────────────────────────────────────────────────
function renderFlight(f, idx, total, animate = false) {
    let route = routeCache.get(f.callsign);
    // If route is implausible, keep airline info but blank out airports
    if (route && !isRoutePlausible(route, f.lat, f.lon)) {
        route = { airlineName: route.airlineName, airlineIata: route.airlineIata };
    }

    // Airline logo + name
    const logoEl = document.getElementById('airline-logo');
    if (route?.airlineIata) {
        logoEl.src = `https://pics.avs.io/200/200/${route.airlineIata}.png`;
        logoEl.classList.remove('no-logo');
    } else {
        logoEl.classList.add('no-logo');
    }
    setAirlineName(route?.airlineName || f.country || '');

    // Flight number + paging
    const csEl = document.getElementById('fl-callsign');
    if (animate) {
        flapAnimate(csEl, f.callsign);
    } else {
        csEl.innerHTML = coloredCallsign(f.callsign);
    }
    const ac = aircraftCache.get(f.icao24);
    document.getElementById('fl-type').textContent    = ac?.type ? `· ${ac.type}` : '';
    document.getElementById('fl-country').textContent = (route?.airlineName && f.country) ? f.country : '';
    document.getElementById('fl-page').textContent    = total > 1 ? `${idx+1} / ${total}` : '';

    // ── Unusual traffic tag ─────────────────────────────────────────────────
    const tagEl     = document.getElementById('fl-tag');
    const airlineEl = document.getElementById('fl-airline');
    if (tagEl && airlineEl) {
        airlineEl.classList.remove('tier-rare', 'tier-uncommon');
        const cls = classifyFlight(f, route);
        if (cls) {
            tagEl.textContent = cls.tag;
            tagEl.className   = `tag-${cls.tier}`;
            airlineEl.classList.add(`tier-${cls.tier}`);
        } else {
            tagEl.textContent = '';
            tagEl.className   = '';
        }
    }

    // Route airports (split-flap cells)
    const origCode = route === undefined ? '···' : (route?.origin || '???');
    const destCode = route === undefined ? '···' : (route?.dest   || '???');
    const origEl = document.getElementById('ap-origin-iata');
    const destEl = document.getElementById('ap-dest-iata');
    if (animate) {
        flapAnimate(origEl, origCode, true);
        flapAnimate(destEl, destCode, true);
    } else {
        origEl.innerHTML = flapText(origCode, true);
        destEl.innerHTML = flapText(destCode, true);
    }
    document.getElementById('ap-origin-city').textContent = route?.originName || '';
    document.getElementById('ap-dest-city').textContent   = route?.destName   || '';

    // Arc
    renderArc(f, route);

    // Stats — animated counters
    animateVal(document.getElementById('sv-alt'),  f.altFt.toLocaleString());
    animateVal(document.getElementById('sv-spd'),  String(f.spd));
    animateVal(document.getElementById('sv-dist'), distFmt(f.distMi));
    document.getElementById('sv-dir').textContent   = f.dir;
    document.getElementById('lbl-dist').textContent = distLbl();
    document.getElementById('lbl-spd').textContent  = spdLbl();

    // Climb rate
    const vsEl   = document.getElementById('sv-vs');
    const vfpmEl = document.getElementById('sv-vfpm');
    const scVs   = document.getElementById('sc-vs');
    const absFpm = Math.abs(f.vrateFpm);

    if (f.vrateFpm > 200) {
        vsEl.className   = 'sc-vs up';
        vsEl.textContent = '↑ Climbing';
        vfpmEl.textContent = `+${absFpm.toLocaleString()} fpm`;
        scVs.className   = 'stat-card vs-up';
    } else if (f.vrateFpm < -200) {
        vsEl.className   = 'sc-vs down';
        vsEl.textContent = '↓ Descending';
        vfpmEl.textContent = `\u2212${absFpm.toLocaleString()} fpm`;
        scVs.className   = 'stat-card vs-down';
    } else {
        vsEl.className   = 'sc-vs lvl';
        vsEl.textContent = '→ Level';
        vfpmEl.textContent = '';
        scVs.className   = 'stat-card vs-lvl';
    }
}

// ─── Unit conversion (API always returns metric) ─────────────────────────────
function toTemp(c)  { return UNITS === 'imperial' ? Math.round(c * 9/5 + 32) : Math.round(c); }
function pressFmt(hPa) {
    return UNITS === 'imperial'
        ? `${(hPa * 0.02953).toFixed(2)} inHg`
        : `${Math.round(hPa)} hPa`;
}

// ─── Render: weather ─────────────────────────────────────────────────────────
let _sunriseTime = null, _sunsetTime = null;

function renderWeather(data) {
    if (!data) return;
    const cur = data.current;
    document.getElementById('temp-val').textContent = `${toTemp(cur.temperature_2m)}${tempLbl()}`;
    document.getElementById('hum-val').textContent  = ` \u00b7 ${Math.round(cur.relative_humidity_2m)}% RH`;
    document.getElementById('pressure-val').textContent = ` \u00b7 ${pressFmt(cur.surface_pressure)}`;

    // Wind speed + direction
    const windEl = document.getElementById('wind-val');
    if (cur.wind_speed_10m != null) {
        const windKt  = kmhToKnots(cur.wind_speed_10m);
        const windDir = toCompass16(cur.wind_direction_10m || 0);
        windEl.textContent = ` \u00b7 ${windKt} kt ${windDir}`;
    } else {
        windEl.textContent = '';
    }

    // Sunrise / sunset (today = index 0)
    const d = data.daily;
    const sunEl = document.getElementById('sun-line');
    if (d.sunrise && d.sunrise[0] && d.sunset && d.sunset[0]) {
        const srTime = new Date(d.sunrise[0]);
        const ssTime = new Date(d.sunset[0]);
        _sunriseTime = srTime; _sunsetTime = ssTime;
        const srStr = srTime.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });
        const ssStr = ssTime.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });
        sunEl.innerHTML = `<span class="sun-arrow">\u2191</span> ${srStr}\u2002\u2002<span class="sun-arrow">\u2193</span> ${ssStr}`;
        updateGoldenHour();
    } else {
        sunEl.textContent = '';
    }

    // Update toggle label
    const btn = document.getElementById('unit-toggle');
    if (btn) btn.textContent = UNITS === 'imperial' ? '°F' : '°C';

    // Forecast grid
    const grid = document.getElementById('forecast-grid');
    grid.innerHTML = '';
    for (let i = 0; i < Math.min(3, d.time.length); i++) {
        const el = document.createElement('div');
        el.className = 'fc-day';
        el.innerHTML = `
            <span class="fc-name">${fmtDayShort(d.time[i], i)}</span>
            <span class="fc-icon">${WMO[d.weather_code[i]] || '🌡️'}</span>
            <div class="fc-temps">
                <span class="fc-max">${toTemp(d.temperature_2m_max[i])}°</span>
                <span class="fc-min"> ${toTemp(d.temperature_2m_min[i])}°</span>
            </div>`;
        grid.appendChild(el);
    }

    // Render flight stats on clock screen
    renderFlightStats();
}

// ─── Golden hour check ───────────────────────────────────────────────────────
function updateGoldenHour() {
    const clockEl = document.getElementById('clock-mode');
    if (!_sunriseTime || !_sunsetTime) { clockEl.classList.remove('golden-hour'); return; }
    const now = Date.now();
    const srMs = _sunriseTime.getTime();
    const ssMs = _sunsetTime.getTime();
    const GOLDEN_MS = 30 * 60 * 1000; // 30 minutes
    const isGolden = (now >= srMs && now <= srMs + GOLDEN_MS)
                  || (now >= ssMs - GOLDEN_MS && now <= ssMs);
    clockEl.classList.toggle('golden-hour', isGolden);
}

// ─── Display mode switching ───────────────────────────────────────────────────
function showClock()  {
    document.getElementById('clock-mode').classList.remove('hidden');
    document.getElementById('flight-mode').classList.add('hidden');
    document.getElementById('hd-right').style.visibility = 'hidden';
}
function showFlight() {
    document.getElementById('clock-mode').classList.add('hidden');
    document.getElementById('flight-mode').classList.remove('hidden');
    document.getElementById('hd-right').style.visibility = 'visible';
}

function updateDisplay() {
    clearTimeout(cycleTimer);
    if (flights.length === 0) {
        showClock();
        document.getElementById('ft-count').textContent = '';
        return;
    }
    showFlight();
    if (fidx >= flights.length) fidx = 0;
    renderFlight(flights[fidx], fidx, flights.length, true);
    document.getElementById('ft-count').textContent =
        `${flights.length} flight${flights.length !== 1 ? 's' : ''} overhead`;
    if (flights.length > 1) {
        cycleTimer = setTimeout(function cycle() {
            fidx = (fidx + 1) % flights.length;
            renderFlight(flights[fidx], fidx, flights.length, true);
            cycleTimer = setTimeout(cycle, FLIGHT_CYCLE_MS);
        }, FLIGHT_CYCLE_MS);
    }
}

// ─── Refresh loops ────────────────────────────────────────────────────────────
function setStatus(msg, ok = true) {
    document.getElementById('ft-msg').textContent = msg;
    document.getElementById('ft-dot').style.background = ok ? 'var(--status-ok)' : 'var(--status-err)';
}

let flightRefreshTimer = null;

async function doFlightRefresh() {
    try {
        setStatus('Scanning\u2026');
        flights = await fetchFlights();
        fidx = 0;
        // Pre-populate aircraft cache from adsb.fi type field
        flights.forEach(f => {
            if (f.type && !aircraftCache.has(f.icao24)) {
                aircraftCache.set(f.icao24, { type: f.type, reg: f.reg || '' });
            }
        });
        updateDisplay();
        setStatus(`Updated ${fmtTime(new Date())}`);
        const uncachedRoutes = flights.filter(f => !routeCache.has(f.callsign));
        const uncachedAC    = flights.filter(f => !aircraftCache.has(f.icao24));
        const fetches = [
            ...uncachedRoutes.map(f => fetchRoute(f.callsign)),
            ...uncachedAC.map(f => fetchAircraft(f.icao24)),
        ];
        if (fetches.length) {
            await Promise.all(fetches);
            if (flights.length > 0) renderFlight(flights[fidx], fidx, flights.length);
        }
        // Log all current flights for stats + last-spotted
        flights.forEach(f => logFlight(f, routeCache.get(f.callsign)));
        renderFlightStats();
    } catch (e) {
        const isRateLimit = /429/.test(e.message);
        setStatus(isRateLimit ? 'Rate-limited, backing off\u2026' : `Error: ${e.message}`, false);
        console.error('Flight fetch:', e);
        if (isRateLimit) { scheduleNextFlightRefresh(FLIGHT_BACKOFF_MS); return; }
    }
    scheduleNextFlightRefresh();
}

function scheduleNextFlightRefresh(overrideMs) {
    clearTimeout(flightRefreshTimer);
    const delay = overrideMs || (flights.length > 0 ? FLIGHT_REFRESH_MS : FLIGHT_IDLE_MS);
    flightRefreshTimer = setTimeout(doFlightRefresh, delay);
}

async function doWeatherRefresh() {
    try { lastWeather = await fetchWeather(); renderWeather(lastWeather); }
    catch (e) { console.warn('Weather fetch:', e); }
}

// ─── Startup ──────────────────────────────────────────────────────────────────
function startApp() {
    document.getElementById('loading').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    loadRouteCache();
    renderFlightStats();
    tickClock();
    setInterval(tickClock, 1000);
    doFlightRefresh();   // schedules its own next refresh
    doWeatherRefresh();
    setInterval(doWeatherRefresh, WEATHER_REFRESH_MS);
}

function showManualForm(reason) {
    document.getElementById('load-msg').textContent = reason;
    document.getElementById('load-hint').classList.add('hidden');
    document.getElementById('loc-form').classList.remove('hidden');
}

document.getElementById('unit-toggle').addEventListener('click', () => {
    UNITS = UNITS === 'imperial' ? 'metric' : 'imperial';
    localStorage.setItem('pt-units', UNITS);
    renderWeather(lastWeather);
});
document.getElementById('unit-toggle').textContent = UNITS === 'imperial' ? '°F' : '°C';

document.getElementById('loc-go').addEventListener('click', () => {
    const lat = parseFloat(document.getElementById('in-lat').value.trim());
    const lon = parseFloat(document.getElementById('in-lon').value.trim());
    const err = document.getElementById('loc-err');
    if (isNaN(lat) || lat < -90  || lat > 90)  { err.textContent = 'Latitude must be −90 to 90';   return; }
    if (isNaN(lon) || lon < -180 || lon > 180)  { err.textContent = 'Longitude must be −180 to 180'; return; }
    err.textContent = '';
    userLat = lat; userLon = lon;
    startApp();
});

if (!window.isSecureContext) {
    console.warn('[GEO] Not a secure context — skipping geolocation');
    showManualForm('GPS unavailable (file:// or HTTP)');
} else if (!navigator.geolocation) {
    console.warn('[GEO] navigator.geolocation not available');
    showManualForm('Geolocation not supported by this browser.');
} else {
    console.log('[GEO] Starting geolocation (secure context, API available)');
    console.log('[GEO] permissions API:', !!navigator.permissions);
    if (navigator.permissions) {
        navigator.permissions.query({ name: 'geolocation' }).then(p => {
            console.log('[GEO] Permission state:', p.state);
        }).catch(e => console.warn('[GEO] Permission query failed:', e));
    }
    let geoAttempt = 0;
    function tryGeo() {
        geoAttempt++;
        console.log(`[GEO] Attempt ${geoAttempt}/3 — calling getCurrentPosition (timeout 8s)`);
        const t0 = Date.now();
        navigator.geolocation.getCurrentPosition(
            pos => {
                console.log(`[GEO] Success in ${Date.now() - t0}ms — lat=${pos.coords.latitude.toFixed(4)}, lon=${pos.coords.longitude.toFixed(4)}, accuracy=${pos.coords.accuracy}m`);
                userLat = pos.coords.latitude; userLon = pos.coords.longitude; startApp();
            },
            err => {
                const codes = { 1: 'PERMISSION_DENIED', 2: 'POSITION_UNAVAILABLE', 3: 'TIMEOUT' };
                console.error(`[GEO] Attempt ${geoAttempt} failed in ${Date.now() - t0}ms — code=${err.code} (${codes[err.code] || 'UNKNOWN'}), message="${err.message}"`);
                if (err.code === 3 && geoAttempt < 2) {
                    document.getElementById('load-msg').innerHTML = 'Still locating<span class="blink">_</span>';
                    tryGeo();
                } else {
                    // GPS failed — try IP-based geolocation as fallback
                    console.log('[GEO] GPS failed, trying IP geolocation fallback');
                    document.getElementById('load-msg').innerHTML = 'Locating by IP<span class="blink">_</span>';
                    fetch('https://get.geojs.io/v1/ip/geo.json')
                        .then(r => r.json())
                        .then(d => {
                            if (d.latitude && d.longitude) {
                                console.log(`[GEO] IP fallback success — lat=${d.latitude}, lon=${d.longitude}, city=${d.city}`);
                                userLat = parseFloat(d.latitude); userLon = parseFloat(d.longitude); startApp();
                            } else {
                                console.error('[GEO] IP fallback returned no coords:', d);
                                showManualForm('Could not determine location — enter manually.');
                            }
                        })
                        .catch(e => {
                            console.error('[GEO] IP fallback failed:', e);
                            showManualForm('Could not determine location — enter manually.');
                        });
                }
            },
            { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 }
        );
    }
    tryGeo();
}
