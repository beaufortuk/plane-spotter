# Plane Spotter

A split-flap style flight tracker that shows aircraft flying overhead in real time. Designed for a Raspberry Pi connected to a screen, but works in any browser.

Built as a single-file web app (`docs/index.html`) with no dependencies, no build step, and no framework — just vanilla HTML, CSS, and JavaScript.

## What it does

- Detects flights overhead using the [OpenSky Network](https://opensky-network.org/) API
- Resolves routes and airline info via [adsbdb](https://www.adsbdb.com/)
- Displays departure/arrival airports with a Solari split-flap animation
- Shows altitude, ground speed, distance, and climb rate
- Renders a flight arc showing the aircraft's position along its route
- Falls back to a clock/weather screen when no flights are overhead
- 3-day weather forecast via [Open-Meteo](https://open-meteo.com/)

## Components

| Directory | What |
|-----------|------|
| `docs/` | The web app — a single `index.html` served via GitHub Pages |
| `macos-widget/` | Native macOS WidgetKit widget (Swift) |
| `worker/` | Cloudflare Worker proxy for OpenSky API |

## Running it

Open `docs/index.html` in a browser. It will ask for your location (or you can enter coordinates manually), then start scanning for flights.

For a dedicated display (e.g. Raspberry Pi), open it in a full-screen Chromium window:

```bash
chromium-browser --kiosk https://beaufortuk.github.io/plane-spotter/
```

## Acknowledgements

This project is a fork of [c0wsaysmoo/plane-tracker-rgb-pi](https://github.com/c0wsaysmoo/plane-tracker-rgb-pi), which tracks flights using a Raspberry Pi and an RGB LED matrix. That project inspired this complete rewrite as a browser-based app with a split-flap display aesthetic. Thanks to c0wsaysmoo for the original idea and for sharing it as open source.
