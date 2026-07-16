# Grandma's Launcher — Phase 1 Design Discussion
## Summary of decisions, observations, and concerns

**Status: ✅ COMPLETE**
*Last updated: Phase 1 shipped — device tested on Redmi Note 8 Pro*

---

## What This Is

An Android launcher designed for elderly, non-literate, and visually impaired users.
The specific real-world user this was designed for: a Dogri-and-Hindi-speaking grandmother
who cannot read or write and is either a first-time smartphone user or very infrequent user.

This document captures the full design discussion that happened before any code was written,
and the implementation lessons learned during the build.

---

## The Core Problem

Modern launchers assume:
- The user can read
- The user understands app icons
- The user knows gestures (swipe, long press, pinch)
- The user can recover from mistakes on their own

None of these are true for the target user.
The launcher must work without any of these assumptions.

---

## Target User Profile

| Attribute | Detail |
|---|---|
| Language | Dogri (primary), Hindi (secondary) — no English |
| Literacy | Cannot read or write in any script |
| Vision | Low vision (age-related), not legally blind |
| Hand tremor | Mild to moderate |
| Device | Budget Android phone, 5.5"–6.5" screen (tested: Redmi Note 8 Pro) |
| Experience | First or very infrequent smartphone user |
| Setup | Done once by a family member or caretaker |

---

## Design Principles (in priority order)

1. **Photo and icon over text** — she navigates by recognition, not reading
2. **One action per screen** — no decision paralysis
3. **Everything visible** — no hidden gestures, no swipe-only navigation
4. **Large touch targets** — minimum 80dp, 100dp+ for primary actions
5. **Hold over tap for dangerous actions** — prevents accidental triggers
6. **Feedback on every interaction** — vibration + visual confirms the press registered
7. **Always a way back** — explicit ← button on every sub-screen
8. **English text for helpers, not for the user** — labels help caretakers during setup

---

## UI Concept Selection

Five concepts were considered:

| Concept | Summary | Verdict |
|---|---|---|
| A — Dashboard | Equal grid of tiles | Rejected — no visual hierarchy |
| B — Column | Vertical priority list | Rejected — utilitarian, no warmth |
| C — Zones | Screen divided into purpose zones | ✅ Selected |
| D — One Big Thing | Single action at a time | Rejected — poor discoverability |
| E — Face-first | Contacts dominate, apps are a strip | Rejected — tool strip too small |

**Concept C (Zones) was selected** because it:
- Creates visual hierarchy that mirrors actual priority
- Puts contacts (photo-first) front and center
- Isolates SOS visually and spatially
- Fits everything on one screen with no scrolling
- Allows generous touch targets for each zone

---

## Key Design Decisions

### Analog Clock Instead of Digital Time
- Grandma (and most elderly users) can read a clock face — it is a lifetime skill
- Reading "10:32" requires numeral literacy which cannot be assumed
- Clock face is language-neutral and universally understood
- Drawn as a custom View (no numerals, just tick marks + hands) — works for fully non-literate users
- **Implementation note:** All paint colours hardcoded as hex literals — never resolved through
  the theme. This ensures the clock looks correct regardless of system dark mode or OEM skin.

### No Text for Primary Navigation
- All text on screen is in English — for caretakers and helpers during setup
- The actual user navigates by: face photos, clock, icons, colour, position
- Future Phase 3 will add recorded voice labels in Dogri/Hindi per button

### Contact Cards: Full-Bleed Photo with Name Overlay
- Originally designed as photo (top) + name below on white space
- **Changed during implementation:** photo now fills the entire card (centerCrop)
- Name overlays the bottom with a dark gradient scrim — readable on any photo colour
- Reasoning: face fills the whole card → faster recognition → feels like a person not a form
- No-photo fallback: each contact gets a distinct colour based on first letter initial

### SOS: Hold-to-Activate, Not Tap
- Tap-to-activate SOS is dangerous for tremor users and fumble scenarios
- Hold 3 seconds with progressive vibration feedback:
  - 0s: short pulse (registered)
  - 1s: medium pulse (keep holding)
  - 2s: strong pulse (almost there)
  - 3s: long burst (call triggered)
- A radial progress ring animates clockwise around the button border as visual companion
- Releasing early resets the ring with no action taken

### Floating Caretaker Button (FAB)
- Present on every screen — not buried in settings
- Bottom-left position (SOS owns bottom-center, separating the two critical elements physically)
- Fades to 25% opacity after 8 seconds of inactivity
- Snaps back to 100% on any screen touch
- Opens a help request screen → sends email to caretaker via mailto: intent (Phase 1)
- Designed as an abstracted action so Phase 2 can swap email → API call without UI changes

### Contact Cards
- Full-bleed photo, fills entire card with centerCrop
- Name overlaid at bottom with gradient scrim for legibility
- Home screen: max 4 contacts (2×2 grid) + "Add" slot + "See All" link
- Contacts screen: 3-per-row photo grid, scrollable, Add slot always at end
- Long press → bottom sheet with Call / Edit / Remove options

### More Apps
- Subtle entry point at bottom of home screen
- Not prominent — Phase 1 users are not expected to use this much
- Available for when they grow comfortable with the device
- Settings accessible only from here — not on home screen

---

## Colour System Rationale

| Colour | Hex | Used for | Why |
|---|---|---|---|
| Warm off-white | `#F7F3EE` | Background | Pure white causes glare for aging eyes |
| Deep blue | `#2B6CB0` | Call actions | Universal communication association |
| Deep green | `#276749` | WhatsApp, confirm YES | WhatsApp brand + "go" signal |
| Amber | `#B7651D` | Caretaker FAB | Warm, human, "needs attention" — not an emergency |
| Deep red | `#C0392B` | SOS only | Appears nowhere else — colour coding only works when unique |

All colour pairs meet WCAG AA contrast minimum. Primary text on background meets AAA (17.2:1).

---

## Caretaker Platform Decision: PWA over Native App

### The question
Should the caretaker companion be a native Android/iOS app or a web app?

### Arguments for native app
- Best push notification reliability (FCM)
- Biometric auth built-in
- Deeper OS integration for future phases

### Arguments for PWA (Progressive Web App)
- One codebase works on Android, iPhone, and desktop
- No app store friction — caretaker opens a link
- Instant updates without store releases
- PWA on Android supports FCM push notifications
- Magic link auth removes password risk entirely

### Decision: PWA
Reasoning:
- Team is small — maintaining three codebases (launcher + Android app + iOS app) is not viable
- Caretakers are not always on Android — a family member on iPhone cannot be excluded
- Security concerns with web are solvable: magic link auth removes credential theft risk,
  HTTPS removes MITM, short-lived JWT tokens handle session security
- PWA closes most of the gap with native on Android which is the primary caretaker device

### Caretaker auth approach: Magic Links
- Caretaker enters email → receives a one-time link → clicks → logged in
- No password to steal, forget, or reuse
- Link expires in 15 minutes
- Sessions tied to device fingerprint — re-auth required on new device

---

## Phase 1 Scope

| Feature | Status |
|---|---|
| Home screen launcher (registered as default) | ✅ Done |
| Analog clock (custom View, real-time) | ✅ Done |
| Photo contacts — local storage | ✅ Done |
| Call confirmation flow | ✅ Done |
| Camera intent (home screen) | ✅ Done |
| WhatsApp intent | ✅ Done |
| SOS hold-to-activate + vibration + progress ring | ✅ Done |
| Caretaker FAB — all screens, fade, email intent | ✅ Done |
| Add / edit / remove contacts | ✅ Done |
| Contacts screen — full photo grid | ✅ Done |
| More Apps screen | ✅ Done |
| Full-bleed contact photo cards | ✅ Done |
| Authentication | ❌ Phase 2 |
| Backend / API | ❌ Phase 2 |
| Remote caretaker configuration | ❌ Phase 2 |
| AI / voice assistant | ❌ Phase 3 |
| Dogri/Hindi voice labels | ❌ Phase 3 |

---

## Implementation Lessons Learned

These are issues discovered during the build on a real device (Redmi Note 8 Pro, MIUI)
that were not anticipated in the design phase. Documented here so Phase 2 starts
with accurate knowledge of the environment.

### 1. MIUI dark mode ignores theme-level settings
**Problem:** `Theme.MaterialComponents.DayNight` followed the system dark mode setting.
On MIUI with dark mode enabled, the warm off-white background went dark, making the
clock hands (dark on dark) invisible.

**Fix applied:**
- Changed theme parent to `Theme.MaterialComponents.Light.NoActionBar`
- Added `android:forceDarkAllowed="false"` to `<application>` in manifest
- Added `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)` in HomeActivity

**Lesson:** Always test on MIUI early. OEM skins frequently override theme contracts
that work correctly on stock Android. Force light mode at all three layers
(theme parent + manifest flag + code) to be safe across all OEMs.

### 2. fitsSystemWindows conflicts with ConstraintLayout
**Problem:** Setting `fitsSystemWindows="true"` on a ConstraintLayout root caused
inconsistent behaviour — the status bar was handled but the navigation bar padding
was either doubled or missing depending on the device.

**Fix applied:** Removed `fitsSystemWindows` from XML entirely. Used
`ViewCompat.setOnApplyWindowInsetsListener` with `setPadding()` directly to apply
exact `systemBars` inset values at runtime.

**Lesson:** `fitsSystemWindows` is reliable on simple layouts (ScrollView, LinearLayout)
but fights with ConstraintLayout. For any complex root layout, handle insets in code.

### 3. resolveActivity() returns null on Android 11+ for camera intents
**Problem:** `intent.resolveActivity(packageManager)` returned `null` on the Redmi
even though MIUI camera was installed. This caused the Add Contact camera flow to
silently do nothing when tapped.

**Root cause:** Android 11 (API 30) introduced package visibility restrictions.
Apps must declare `<queries>` in the manifest to see other packages, otherwise
`resolveActivity()` returns null even when the target app is present.

**Fix applied:**
- Added `<queries>` block to manifest declaring camera intent actions and MIUI package name
- Replaced `resolveActivity()` guard with a `try/catch` that always attempts the intent
- Added `FLAG_GRANT_WRITE_URI_PERMISSION` and `FLAG_GRANT_READ_URI_PERMISSION` to the
  camera intent so MIUI camera can write to the FileProvider URI

**Lesson:** Never use `resolveActivity()` as a camera/gallery guard on API 30+.
Always use try/catch. Always declare `<queries>` for any implicit intents.

### 4. Photo clip requires MaterialCardView wrapper
**Problem:** Full-bleed photos bled outside the rounded corners of contact cards
because the rounded corner was defined as a background drawable — drawables do not
clip their sibling views.

**Fix applied:** Wrapped each contact card in a `MaterialCardView` with the same
corner radius. `MaterialCardView` uses `clipChildren = true` and proper outline
clipping, which correctly clips the photo to the rounded shape.

**Lesson:** For rounded corners that clip content (especially images), always use
`MaterialCardView` or `ShapeableImageView`. Background drawables with corner radius
only round the background — they do not clip child views.

---

## Open Questions Resolved

| Question | Resolution |
|---|---|
| Emergency number hardcoded or configurable? | Defaults to 112, stored in AppPreferences — caretaker can change in Phase 2 |
| Contact photo: copy or URI reference? | Copied to app private storage — URIs break when source is deleted |
| Clock numerals? | Tick marks only — numeral-free for Phase 1 |
| WhatsApp fallback? | Shows Toast if not installed — Phase 2 can add SMS fallback |
| Max contacts on home? | 4 (2×2 grid) — fills screen without crowding |

## Open Questions Remaining for Phase 2

1. Should the caretaker be able to lock the "More Apps" entry point remotely?
2. Should there be a PIN-protected settings screen accessible from More Apps?
3. What happens when the caretaker email bounces — silent failure or on-screen message?
4. Should contacts sync across devices if grandma gets a new phone?

---

## Observations and Concerns

### Concern: Accidental SOS triggers
Mitigated by hold-to-activate. But if the user falls and the phone is pressed against
something for 3 seconds, SOS could trigger unintentionally.
Possible future mitigation: require two-zone simultaneous press, or add a cancel screen
with a 5-second window after trigger.

### Concern: User getting stuck on a sub-screen
Every sub-screen has an explicit ← back button. The home screen is always reachable.
The system back gesture / button also works as a secondary escape.

### Concern: Caretaker email reliability
Email has latency — not suitable for genuine emergencies. The caretaker button is for
non-emergency help requests ("I can't figure out how to use the camera").
SOS handles genuine emergencies via phone call, which is instant and reliable.

### Concern: Add Contact flow complexity
The flow requires a caretaker or helper to complete in Phase 1.
The user cannot add contacts independently without literacy.
Phase 3 voice features will allow adding a contact by holding the phone up to someone
and saying their name in Dogri/Hindi.

### Observation: Dogri/Hindi voice layer is critical for long-term usability
Even with perfect visual design, the user will feel more confident if the phone speaks
to her in Dogri. This is a Phase 3 feature but the design is intentionally structured
so every button has an associated audio slot — this can be populated in Phase 2 by
caretaker-recorded audio.

### Observation: This design works for more than one user profile
The design decisions made for a non-literate Dogri-speaking grandmother also benefit:
- Elderly users with vision decline
- Users with cognitive impairments
- Users in any language/script not supported by standard launchers
- Anyone experiencing their first smartphone

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin | Modern Android standard |
| Min SDK | API 26 (Android 8.0) | 95%+ device coverage |
| UI | XML Layouts + Views | Precise control for custom clock and SOS ring |
| Storage | SharedPreferences + internal file storage | No backend needed for Phase 1 |
| Libraries | AndroidX + Material Components only | Minimal footprint, no supply chain risk |

---

## Phase 2 Starting Point

Phase 2 is the **Caretaker System**. Based on Phase 1 decisions and lessons,
here is the recommended starting point:

### Immediate priorities
1. **Caretaker PWA** — Next.js app, magic link auth, hosted on Vercel
2. **Caretaker email setup screen** — In-app screen (PIN-protected) where a helper
   enters the caretaker's email address. Currently this is set via AppPreferences
   with no UI.
3. **Emergency number setup screen** — Same as above for the SOS number.
4. **Replace mailto: with API call** — The CaretakerHelpActivity already abstracts
   the send action behind a single method — just swap the implementation.

### Architecture decisions needed before Phase 2
- Device ↔ server sync protocol (REST vs WebSocket)
- Contact sync strategy (does the caretaker portal manage contacts remotely?)
- Auth token storage on the Android side (EncryptedSharedPreferences)
- Whether to use Firebase for push notifications or a self-hosted solution

### What not to change in Phase 2
- All Phase 1 UI — grandma's experience must not regress
- Contact storage format — keep SharedPreferences + JSON, add a sync layer on top
- The FAB action abstraction — already designed to accept Phase 2 API calls

---

*Phase 1 completed and tested on Redmi Note 8 Pro (MIUI)*
*Phase 2: Caretaker System — PWA + remote configuration*
*Phase 3: Local LLM + voice assistant in Dogri/Hindi*
