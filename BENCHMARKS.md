# Grandma's Launcher — Benchmarks

Micro-benchmarks of the launcher's core paths. Runs on the JVM via Robolectric
(no device/emulator needed). Rendering benchmarks use Robolectric **native graphics**
(real Skia software rendering into a real Bitmap), so the draw timings are
representative of software rasterization; GPU-accelerated rendering on-device is
expected to be faster.

## Environment

| Attribute | Value |
|---|---|
| Host | macOS, Apple Silicon (arm64) |
| JDK | Temurin 21.0.4 LTS |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.0.21 |
| Robolectric | 4.14.1 (API 34, native graphics) |
| Renderer | Skia, software raster (no GPU) |
| Repository commit | benchmark suite added |

## How to run

```bash
./gradlew :app:testDebugUnitTest --tests "com.grandma.launcher.benchmark.*"
```

Each test warms up (JIT tier-up) before measuring, then reports mean / median /
p90 / p95 / min / max per operation plus throughput. Results also land in
`app/build/reports/tests/testDebugUnitTest/`.

## 1. Crypto — RSA-2048 challenge/response (`DeviceSecurityManager`)

The device-verification handshake used in the caretaker backend flow.

| Operation | mean | median | p90 | p95 | throughput |
|---|---|---|---|---|---|
| RSA-2048 key pair generation (one-time) | 439 ms | — | — | — | ~2.3 gen/sec |
| `signChallenge()` SHA256withRSA (includes PEM parse) | 2.75 ms | 2.47 ms | 3.32 ms | 4.64 ms | 364 ops/sec |
| `verifySignature()` SHA256withRSA | 121 us | 98 us | 138 us | 180 us | 8,281 ops/sec |

Observations:
- Key generation is ~0.4 s — do it once at registration, off the main thread
  (already handled by `ensureKeyPair` being invoked lazily on first use).
- Signing at ~2.5 ms is negligible vs. any network round-trip to the backend.
- Signature round-trip verified correctly; tampered challenges are rejected.

## 2. Storage — contact list (SharedPreferences + JSON, `ContactRepository`)

Phase 1 stores contacts as a JSON array in SharedPreferences. List sizes cover
the realistic 5–15 range plus a stress case of 50.

| Operation | n | mean | median | p90 | p95 | throughput |
|---|---|---|---|---|---|---|
| `getAll()` read | 5 | 23 us | 11 us | 14 us | 16 us | 44,139 ops/s |
| `save()` write | 5 | 56 us | 44 us | 56 us | 85 us | 17,763 ops/s |
| `getFavourites()` filter | 5 | 12 us | 6 us | 7 us | 8 us | 85,010 ops/s |
| `getAll()` read | 15 | 21 us | 17 us | 22 us | 25 us | 47,608 ops/s |
| `save()` write | 15 | 75 us | 61 us | 80 us | 110 us | 13,250 ops/s |
| `getFavourites()` filter | 15 | 22 us | 17 us | 19 us | 22 us | 45,432 ops/s |
| `getAll()` read | 50 | 58 us | 54 us | 63 us | 76 us | 17,252 ops/s |
| `save()` write | 50 | 146 us | 130 us | 169 us | 197 us | 6,832 ops/s |
| `getFavourites()` filter | 50 | 77 us | 54 us | 65 us | 77 us | 12,987 ops/s |

Observations:
- Read + filter stay far below a frame budget even at 3× the realistic contact
  count. Storage is not a bottleneck for Phase 1.
- `save()` re-serialises the entire list on every write — an O(n) JSON rebuild.
  Fine at these sizes; a Phase 2 sync layer should keep the same storage contract.

## 3. Settings — `AppPreferences`

| Operation | mean | median | p90 | p95 | throughput |
|---|---|---|---|---|---|
| `emergencyNumber` get/set | 56 us | 19 us | 37 us | 70 us | 17,793 ops/s |
| `addCaretakerEmail()` | 9 us | 2 us | 4 us | 7 us | 109,834 ops/s |
| `getCaretakerEmails()` split + dedupe | 1.5 us | 1.0 us | 1.1 us | 1.5 us | 661,084 ops/s |
| `verifyPin()` | 29 us | 18 us | 26 us | 42 us | 34,958 ops/s |
| first `deviceId()` (UUID + persist, one-time) | 2.4 ms | — | — | — | — |

## 4. Logic / data operations

| Operation | mean | median | p90 | p95 | throughput |
|---|---|---|---|---|---|
| `setFavourite()` (map + copy over 15) | 33 us | 30 us | 44 us | 50 us | 30,499 ops/s |
| `indexOfFirst` lookup (15) | 193 ns | 166 ns | 208 ns | 209 ns | 5.19 M ops/s |
| `filter` favourites (15) | 500 ns | 375 ns | 500 ns | 542 ns | 2.00 M ops/s |
| `WeatherRepository.getCurrentWeather()` | 1.2 us | 1.1 us | 1.2 us | 1.5 us | 872,390 ops/s |

## 5. Rendering — custom views (real Skia, software)

Per-frame cost of `View.draw()` (includes `onDraw`). A 60 fps display gives a
16.6 ms frame budget — every case is well under it.

| View | state | mean | median | p90 | p95 | frames/sec |
|---|---|---|---|---|---|---|
| `AnalogClockView` 540×540 | face + 12 ticks + hands | 658 us | 465 us | 882 us | 1.23 ms | ~1,520 |
| `SosButtonView` 480×480 | idle, no ring | 77 us | 61 us | 70 us | 90 us | ~13,000 |
| `SosButtonView` 480×480 | half progress ring | 156 us | 103 us | 218 us | 291 us | ~6,400 |
| `SosButtonView` 480×480 | full progress ring | 233 us | 143 us | 327 us | 465 us | ~4,285 |

Observations:
- The SOS progress ring (a `PathMeasure` + `getSegment` recomputation per frame)
  triples the draw cost of the idle button (233 us vs 77 us). Still cheap, but if
  the ring ever needs to render faster, cache the segment path per progress step
  or draw the ring as arcs (`drawArc`) instead of path segments.
- `AnalogClockView.onDraw` re-resolves six colours through `ContextCompat.getColor`
  and calls `Calendar.getInstance()` every frame. Negligible in isolation; moving
  both to cached values would trim ~40 us off the worst case if it ever matters.

## Summary

Everything the launcher does on its hot paths is orders of magnitude inside the
16.6 ms per-frame budget:
- Storage (contacts): **µs-scale**, even at 50 contacts.
- Rendering: **0.08–0.66 ms** per frame (software), worst case p95 1.2 ms.
- The only heavyweight operation is RSA-2048 key generation (~440 ms, one-time)
  and signing (~2.5 ms) — both are off the UI hot path by design.