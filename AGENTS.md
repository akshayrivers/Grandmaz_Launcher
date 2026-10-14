# AGENTS.md — sys-launcher (Grandma's Launcher, Android)

Guidance for AI coding agents working in this repo. Read the System overview first, then the
Product/UX rules — they are binding and override "normal" Android UX habits.

## System overview (shared across all three repos)

One product, three sibling repos. The user is a Dogri/Hindi-speaking grandmother who cannot read,
has low vision and a hand tremor. Family caretakers configure and watch her phone remotely.

| Repo | Path (from any repo root) | Role |
|---|---|---|
| sys-launcher | `../sys-launcher` | Android launcher (Kotlin, XML Views) on grandma's phone |
| dadi's Backend | `../dadi's Backend` | Fastify + Postgres API: devices, caretaker links, commands, snapshots, help requests |
| Dadi's Caretaker | `../Dadi's Caretaker` | Caretaker web app (React Router SSR); must become an installable PWA with push |

Core flows (all under `/api`):
1. **Device registration** — launcher creates an RSA-2048 key pair, `POST devices/register`
   `{deviceId, publicKey, deviceMetadata}` → gets `challenge` (fallback `POST devices/challenge`) →
   signs it SHA256withRSA, base64 → `POST devices/verify-signature`.
2. **Caretaker linking** — on grandma's phone a helper enters only the caretaker's email →
   `POST invitations` → backend emails a magic link to `CARETAKER_WEB_URL/accept-invitation?token=…`
   → caretaker signs in with Firebase on *their own* device → `POST auth/accept-invitation` creates a
   `device_caretakers` row. Pending invitations are also auto-accepted on `auth/verify` / `GET devices`.
3. **Commands** — caretaker `POST commands` → `tasks` row (`pending`) → launcher polls
   `GET commands/device/:deviceId?status=pending` → executes → `PATCH commands/:id/status`
   (`running` → `completed`/`failed`, with `result`).
4. **Snapshots** — launcher `POST shared-state/snapshot` (battery, wifi, storage, apps, settings);
   caretaker reads `shared-state/device/:id/latest|history`.
5. **Help requests** — launcher `POST help-requests` `{deviceId, title, type: general|sos|medical|tech_support}`;
   caretaker lists and resolves them.

API contract rule: when an endpoint changes, update the backend, the launcher (`remote/*Repository`,
`network/ApiClient.kt`) and the caretaker app (`app/lib/api.ts`, `app/lib/types.ts`) in the same change.
Known mismatch: device requests use camelCase; backend rows are snake_case and some services return
both (`deviceId` + `device_id`, `isVerified` + `is_verified`). Don't add more dual-key responses — pick
camelCase for new fields and convert old ones deliberately across all three repos.

## Stack & commands

Kotlin 2.0.21, AGP 8.13.2, compileSdk/targetSdk 36, minSdk 26, Java 1.8 target, ViewBinding on.
Deps: AndroidX core/appcompat/constraintlayout, Material, androidx.credentials, play-services-auth.
Tests: JUnit 4 + Robolectric 4.14.1 (JVM, no emulator). JDK 21 works. No ktlint/detekt configured.

```bash
./gradlew :app:assembleDebug                 # build APK
./gradlew :app:installDebug                  # install on connected device
./gradlew :app:testDebugUnitTest             # all JVM unit tests (Robolectric)
./gradlew :app:testDebugUnitTest --tests "com.grandma.launcher.benchmark.*"   # benchmarks (BENCHMARKS.md)
./gradlew :app:lint                          # Android Lint (AGP default)
```

Backend URL: `AppPreferences.backendBaseUrl` (default constant `DEFAULT_BACKEND_BASE_URL`).
Run the backend from `../dadi's Backend` with `npm run dev` (port 3000) for end-to-end work.

## Architecture map (`app/src/main/java/com/grandma/launcher/`)

| Path | Responsibility |
|---|---|
| `data/AppPreferences.kt` | All settings in SharedPreferences `grandma_app_prefs` (caretaker emails, PIN, SOS number, backend URL, deviceId, keys, verified flag) |
| `data/ContactRepository.kt`, `Contact.kt` | Contacts as a JSON array in SharedPreferences; photos copied to app-private storage |
| `data/WeatherRepository.kt` | Weather (currently simulated — see Known issues) |
| `network/ApiClient.kt` | `object` wrapping `HttpURLConnection`; returns sealed `ApiClient.Result.Success/Error` |
| `network/DeviceSecurityManager.kt` | RSA key generation, PEM encode/parse, `signChallenge` |
| `remote/DeviceRegistrationRepository.kt` | register → challenge → verify, guarded by a `Mutex` |
| `remote/DeviceSyncManager.kt` | Singleton loop: ensure verified, post snapshot every 30 s |
| `remote/CommandExecutionManager.kt` | Poll pending tasks, `when(command)` dispatch, report status |
| `remote/StateSnapshotRepository.kt`, `HelpRequestRepository.kt`, `CaretakerInvitationRepository.kt` | One repository per backend resource |
| `ui/home/` | `HomeActivity` (launcher HOME intent, `singleTask`), header, contact cards, weather card |
| `ui/contacts/` | Contacts grid, add/edit, call-confirm and options bottom sheets |
| `ui/caretaker/` | Floating caretaker bubble service, help screen, PIN dialog |
| `ui/setup/SetupActivity.kt` | One-time caretaker setup wizard |
| `ui/alarm/`, `ui/apps/` | Alarms (receiver + UI), More Apps |
| `ui/views/` | Custom-drawn views: `AnalogClockView`, `SosButtonView`, `WeatherIconView`, time pickers |
| `res/values/colors.xml` | Documented colour system — every colour has a stated reason |
| `app/src/test/.../benchmark/` | Robolectric micro-benchmarks (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) |

## Code conventions

- Package-by-layer: `data/` (local storage), `network/` (transport + crypto), `remote/` (one
  `XxxRepository`/`XxxManager` per backend resource), `ui/<feature>/` (Activities, Views, sheets).
- UI is XML layouts + ViewBinding + custom `View`s. No Compose, no DI framework — classes take a
  `Context` and construct `AppPreferences(context)` themselves. Keep it that way unless asked.
- Networking: suspend functions on `Dispatchers.IO`, return `ApiClient.Result<T>`; never throw to the UI.
  Endpoints are relative strings like `"api/devices/register"`. Build JSON with `org.json`.
- Activities launch work via `lifecycleScope`; long-lived sync lives in `DeviceSyncManager`'s
  `SupervisorJob` scope. Heavy work (key generation ~440 ms) must stay off the main thread.
- Preferences: add a `KEY_*` constant in the companion object and a typed property; never read
  raw prefs elsewhere. Keep the contacts storage format stable (sync layers sit on top).
- New remote commands: add a branch in `CommandExecutionManager.executeCommand`, return
  `{success, message}`, and add the command to the caretaker app's command UI.
- Device lessons from `phase1-discussion.md` (MIUI, Redmi Note 8 Pro): force light mode at theme +
  manifest (`forceDarkAllowed=false`) + code; handle insets with `ViewCompat.setOnApplyWindowInsetsListener`
  (not `fitsSystemWindows` on ConstraintLayout); never guard implicit intents with `resolveActivity()` —
  use try/catch and declare `<queries>`; clip photos with `MaterialCardView`/`ShapeableImageView`;
  hardcode clock paint colours.
- Logging: `Log.e(TAG, …)` with a `companion object TAG`; never log phone numbers, emails, names or keys.

## Product / UX rules (binding)

Source: `phase1-discussion.md` plus later product decisions. Do not regress grandma's experience.

1. **Photo/icon over text.** She navigates by faces, colour, position and the clock — not words.
2. **One action per screen.** No multi-choice forms for grandma.
3. **Everything visible.** No hidden gestures, swipe-only or long-press-only paths for her actions
   (long-press is acceptable only for helper/caretaker functions).
4. **Touch targets:** minimum 80dp; primary actions 100dp+.
5. **Hold for dangerous actions** (SOS = 3 s hold with progressive vibration + radial ring; release resets).
6. **Feedback on every interaction:** picture + voice + vibration. Do not use Toasts as feedback for
   grandma (existing Toasts in setup/help/home are helper-facing or legacy — don't add more).
7. **Always a way back:** big home button / explicit back on every sub-screen, and inactivity
   automatically returns to Home.
8. **Colour meanings are fixed:** red `#C0392B` is SOS only — nowhere else; blue `#2B6CB0` = call;
   green `#276749` = WhatsApp / yes; amber `#B7651D` = caretaker; background warm off-white `#F7F3EE`.
   Use the named colours in `colors.xml`; any new colour needs a documented reason.
9. **Analog clock**, tick marks only, no numerals.
10. **English text is for helpers only**; grandma-facing labels are spoken, not read.

Product decisions to build toward:
- **Voice:** Hindi TTS primary, English fallback, plus per-button custom voice clips that caretakers
  can re-record from launcher caretaker settings and from the caretaker app.
- **SOS:** when the 3 s hold completes, immediately (a) send an alert with live location to *all*
  caretakers and (b) call the **primary** caretaker (`ACTION_CALL`, fall back to `ACTION_DIAL`). Any
  cancel window may only stop the alerts — never the call. With no data connection, fall back to an
  SMS with location (`SEND_SMS`; also needs location permissions — neither is in the manifest yet).
- **Caretaker setup:** on grandma's phone only enter the caretaker email; the caretaker logs in on
  their own device and the backend verifies/links. **No Google sign-in on grandma's phone** — the
  existing Google/Credential Manager code in `SetupActivity` is to be removed, not extended.
- **Primary caretaker:** whoever links first becomes primary; the primary can transfer the role.
  Exactly one primary per device (the backend is the source of truth; the launcher caches it for SOS).
- **Caretaker PIN** can only be set/reset on grandma's phone (PIN dialog / caretaker settings). Never
  add a remote command, sync field or snapshot field that sets, resets or reveals it.
- **Remote access:** the server is the intermediary. Config = a versioned per-device config document
  pulled by the launcher; commands are queued server-side; FCM push only *wakes* the device to sync.
  Must work offline (apply on reconnect) and every change is audited. Live screen viewing comes later
  via WebRTC (server does signaling + TURN fallback only); full remote control is deferred.
- **Distribution:** Google Play *and* direct APK on GitHub Releases; fully open source. If Play policy
  blocks a permission (e.g. `SEND_SMS`, `CALL_PHONE`, `QUERY_ALL_PACKAGES`), split into product flavors
  (`play` restricted vs `github` full) instead of dropping the feature; gate code with `BuildConfig`.
- **Budget:** near-zero — prefer free tiers and open-source, self-hostable components. Hosting/storage
  aren't chosen yet: keep the backend base URL in `BuildConfig` (per flavor/build type), not code.
- **Contacts:** Home shows 4 by default, expandable to the full photo grid; importable from system
  contacts; multiple numbers per contact; caretakers can edit contacts remotely.
- **WhatsApp:** direct video call to a specific person (not just opening WhatsApp).
- **Weather:** real data (replace the simulator).
- **AI assistant:** Gemini (free models / Live API) for now, local LLM later; KittenTTS optional.
  A free-tier key is for local testing only; production gets backend-issued ephemeral tokens.

## Security rules

- Never commit secrets: `local.properties`, keystores (`*.jks`, `*.keystore`), `google-services.json`,
  service-account JSON, API keys. Don't hardcode keys in Kotlin or `strings.xml`.
- **Never ship an API key in the APK** (Gemini included — APKs are trivially decompiled and this repo
  is public). Fetch short-lived Gemini tokens from the backend.
- The caretaker PIN never leaves the device (not in snapshots, sync or logs).
- Every device call to the backend must authenticate (device token / signature once implemented);
  don't add new unauthenticated endpoints usage.
- Store keys/tokens in Android Keystore or EncryptedSharedPreferences, not plain SharedPreferences.
- Production traffic must be HTTPS; don't widen `usesCleartextTraffic`.
- Don't log or send PII beyond what a feature needs (contacts, phone numbers, location, emails).

## Known issues / current state (to be fixed — do not copy these patterns)

- Private RSA key is stored as PEM in plain SharedPreferences (`devicePrivateKeyPem`) → move to Android Keystore.
- Backend URL is a hardcoded LAN IP over cleartext HTTP (`usesCleartextTraffic="true"`) → move to `BuildConfig`.
- SOS (`HomeActivity.triggerSos`) calls the emergency number (default 112), not the primary caretaker,
  and posts an `sos` help request without location; no SMS fallback yet.
- `update_caretaker` command overwrites caretaker email remotely; no FCM, config document or flavors yet.
- Device has no auth token after verification, so command polling (`GET commands/device/...`, which
  requires Firebase auth on the backend) returns 401 and remote commands never execute.
- `WeatherRepository` is fake (time-of-day simulation, "Jammu").
- `SetupActivity` still contains Google sign-in; help requests don't yet notify caretakers.
- Only benchmark tests exist — add real unit tests (Robolectric) for logic you touch.

## Workflow rules

- Small, focused changes; one concern per change. Don't reformat unrelated code.
- Before finishing: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lint` (at least assembleDebug).
- Cross-repo API changes ship together (see System overview).
- Git commits must never include `Co-authored-by` trailers for Cursor / Cursor Agent; author as the user only.
