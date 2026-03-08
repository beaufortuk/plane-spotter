# Test Coverage Analysis

## Current State

**The project has zero tests.** There are no test files, no test configuration, and no test
dependencies (pytest, unittest, etc.) in the project. This analysis identifies the most
valuable areas to add test coverage, prioritized by risk and complexity.

---

## Priority 1 — Pure Utility Functions (High Value, Easy to Test)

These are self-contained functions with no external dependencies. They are the best starting
point for building a test suite.

### `utilities/overhead.py` — Math & Data Helpers

| Function | What it does | Why it needs tests |
|---|---|---|
| `haversine(lat1, lon1, lat2, lon2)` | Computes distance between two GPS coordinates | Core to the app's distance calculations. Has a metric/imperial branch. Easy to verify against known values. |
| `degrees_to_cardinal(deg)` | Converts compass degrees to cardinal direction (N, NE, etc.) | Edge cases at boundaries (0°, 360°, 359°, negative values). The `idx % 8` logic should be verified. |
| `plane_bearing(flight, home)` | Computes bearing from home to a flight | Trigonometry that's easy to get wrong. Test with known city pairs. |
| `ordinal(n)` | Returns "1st", "2nd", "3rd", "4th", etc. | Clever one-liner with tricky edge cases (11th, 12th, 13th, 21st, 112th). |
| `safe_load_json(path)` / `safe_write_json(path, data)` | JSON file I/O with error handling | Test with missing files, corrupt JSON, non-list data, and normal round-trip. |

### `web/map_generator.py` — Geo Math

| Function | What it needs |
|---|---|
| `great_circle_points(start, end, steps)` | Verify against known great-circle paths. Test the `d == 0` (same-point) edge case. |
| `normalize_longitudes(points)` | Test antimeridian crossing (e.g., 179° to -179°). |
| `align_to_reference_tile(lon, ref_lon)` | Same antimeridian logic — verify wrapping in both directions. |
| `get_unit_label()` | Simple but worth a quick assertion for imperial vs metric. |

### `setup/email_alerts.py` — Formatters

| Function | What it needs |
|---|---|
| `get_timestamp()` | Verify 12hr vs 24hr format output. |
| `format_dist(v)` | Verify metric ("km") vs imperial ("miles") output. |

### `utilities/temperature.py` — Helpers

| Function | What it needs |
|---|---|
| `is_dns_error(exc)` | Test with a real `socket.gaierror` wrapped in a `RequestException`, and with non-DNS exceptions. |

---

## Priority 2 — Business Logic (High Value, Medium Effort)

These functions contain the app's most important decision-making logic. They require mocking
file I/O and external services, but are critical to get right.

### `utilities/overhead.py` — Flight Logging

| Function | What it needs |
|---|---|
| `log_flight_data(entry)` | **Most complex logic in the codebase.** Test: (1) new flight enters top-N, (2) existing flight updates with better distance, (3) existing flight ignored when distance is worse, (4) flight that doesn't make top-N is excluded, (5) list stays sorted and capped at `MAX_CLOSEST`. Mock `email_alerts`, `map_generator`, `upload_helper`, and file I/O. |
| `log_farthest_flight(entry)` | Similar complexity. Test: (1) new airport enters top-N, (2) existing airport updates with closer distance, (3) airport rejected when farthest value is too small, (4) list stays sorted descending and capped at `MAX_FARTHEST`. Also test the `notify` vs `updated` distinction (only new airports trigger email). |

### `utilities/overhead.py` — `Overhead._grab()`

| What it needs |
|---|
| Mock `FlightRadar24API` and test: (1) flights are filtered by altitude, (2) flights are sorted by distance, (3) flights are capped at `MAX_FLIGHT_LOOKUP`, (4) retry logic works (retries on exception, stops after `RETRIES` attempts), (5) connection errors are caught gracefully. |

### `utilities/overhead.py` — `Overhead.safe_get()`

| What it needs |
|---|
| Test nested dict traversal with missing keys, `None` values, non-dict intermediates, and the `default` parameter. |

---

## Priority 3 — Web Layer (Medium Value, Medium Effort)

### `web/app.py` — Flask Routes

| Route | What it needs |
|---|---|
| `GET /` | Returns 200 with HTML. |
| `GET /closest/json` | Returns valid JSON. Test with missing file (should return `{}`). |
| `GET /farthest/json` | Returns valid JSON. Test with missing file (should return `[]`). |
| `GET /maps/<filename>` | Serves static files. Test 404 for missing files. |

Flask provides a built-in test client (`app.test_client()`) making these straightforward.

### `web/upload_helper.py`

| Function | What it needs |
|---|---|
| `get_upload_token()` | Mock `requests.get`. Test happy path, server error, and timeout. |
| `upload_map_to_server(path)` | Mock `requests.post`. Test with missing file, empty token, successful upload, and server error. |

---

## Priority 4 — Scene / Display Logic (Lower Value, Higher Effort)

The scene classes (`FlightDetailsScene`, `JourneyScene`, `ClockScene`, etc.) are tightly
coupled to `rgbmatrix` hardware libraries that aren't available in a standard test
environment. Testing these requires either:

- **Stubbing out `rgbmatrix`** with a fake module at import time
- **Extracting testable logic** from rendering code

### Specific areas worth extracting and testing:

| Scene | Testable Logic |
|---|---|
| `FlightDetailsScene` | Callsign formatting: stripping ICAO prefix, prepending airline name. The scrolling index wrapping logic. |
| `JourneyScene` | Delay-to-color mapping (the long `if/elif` chains for departure and arrival delays). Arrow pixel ratio calculation based on origin/destination distance. |
| `Animator` | KeyFrame registration via introspection. Frame divisor/offset scheduling logic. |

---

## Priority 5 — Integration / Smoke Tests

| Test | Purpose |
|---|---|
| Config loading | Verify the app handles missing optional config values gracefully (the `try/except ImportError` patterns throughout). |
| End-to-end data pipeline | With a fully mocked `FlightRadar24API`, verify that `Overhead.grab_data()` produces correctly structured entries with all expected keys. |
| Email formatting | `send_flight_summary` builds the email body correctly with all fields, with/without reason, with/without map URL. |

---

## Recommended Test Infrastructure

1. **Add `pytest` to requirements** (or create a `requirements-dev.txt`)
2. **Create a `tests/` directory** at the project root with:
   - `tests/conftest.py` — shared fixtures (mock flight data, temp directories for JSON files)
   - `tests/test_overhead_utils.py` — Priority 1 pure functions
   - `tests/test_overhead_logging.py` — Priority 2 logging logic
   - `tests/test_overhead_class.py` — Priority 2 Overhead class
   - `tests/test_web_app.py` — Priority 3 Flask routes
   - `tests/test_map_generator.py` — Priority 1 geo math
   - `tests/test_email_alerts.py` — Priority 1 formatters + Priority 2 email building
   - `tests/test_upload_helper.py` — Priority 3 upload logic
   - `tests/test_temperature.py` — Priority 2 weather API (mocked)
3. **Add a `pytest.ini` or `pyproject.toml` section** with sensible defaults
4. **Mock strategy**: Use `unittest.mock.patch` for external APIs (FlightRadar24, Tomorrow.io, SMTP, upload server) and `tmp_path` fixtures for file I/O

---

## Summary

| Priority | Area | Estimated Tests | Effort |
|---|---|---|---|
| P1 | Pure utility functions | ~25-30 | Low |
| P2 | Flight logging & Overhead class | ~20-25 | Medium |
| P3 | Flask routes & upload helper | ~10-15 | Medium |
| P4 | Scene/display logic (extracted) | ~10-15 | High |
| P5 | Integration / smoke tests | ~5-10 | Medium |
| **Total** | | **~70-95** | |

Starting with **Priority 1** would give immediate confidence in the mathematical core of the
application with minimal setup effort. **Priority 2** should follow quickly, as the logging
functions contain the most complex branching logic and are the most likely source of subtle
bugs.
