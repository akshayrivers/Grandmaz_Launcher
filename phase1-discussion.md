# Grandma's Launcher — Phase 1 Design Discussion
## Summary of decisions, observations, and concerns

---

## What This Is

An Android launcher designed for elderly, non-literate, and visually impaired users.
The specific real-world user this was designed for: a Dogri-and-Hindi-speaking grandmother
who cannot read or write and is either a first-time smartphone user or very infrequent user.

This document captures the full design discussion that happened before any code was written.

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
| Device | Budget Android phone, 5.5"–6.5" screen |
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

### No Text for Primary Navigation
- All text on screen is in English — for caretakers and helpers during setup
- The actual user navigates by: face photos, clock, icons, colour, position
- Future Phase 3 will add recorded voice labels in Dogri/Hindi per button

### SOS: Hold-to-Activate, Not Tap
- Tap-to-activate SOS is dangerous for tremor users and fumble scenarios
- Hold 3 seconds with progressive vibration feedback:
  - 0s: short pulse (registered)
  - 1s: medium pulse (keep holding)
  - 2s: strong pulse (almost there)
  - 3s: long burst (call triggered)
- A radial progress ring animates around the button border as visual companion
- Releasing early resets everything with no action taken

### Floating Caretaker Button (FAB)
- Present on every screen — not buried in settings
- Bottom-left position (SOS owns bottom-center, separating the two critical elements physically)
- Fades to 25% opacity after 8 seconds of inactivity
- Snaps back to 100% on any screen touch
- Opens a help request screen → sends email to caretaker (Phase 1)
- Designed as an abstracted action so Phase 2 can swap email → API call without UI changes

### Contact Cards
- Photo-first, circular crop, 88dp on home screen
- Name below the photo — reinforces recognition after face
- Home screen: max 4 contacts (2×2 grid) + "Add" slot + "See All" link
- Contacts screen: 3-per-row photo grid, scrollable, Add slot always at end
- Long press → bottom sheet with Call / Edit / Remove options

### More Apps
- Subtle entry point at bottom of home screen ("• • • More Apps")
- Not prominent — Phase 1 users are not expected to use this much
- Available for when they grow comfortable with the device
- Settings is accessible only from here — not on home screen

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
- Security concerns with web are solvable: magic link auth removes credential theft risk, HTTPS removes MITM, short-lived JWT tokens handle session security
- PWA closes most of the gap with native on Android which is the primary caretaker device

### Caretaker auth approach: Magic Links
- Caretaker enters email → receives a one-time link → clicks → logged in
- No password to steal, forget, or reuse
- Link expires in 15 minutes
- Sessions tied to device fingerprint — re-auth required on new device

---

## Phase 1 Scope (what we are building now)

| Included | Excluded |
|---|---|
| Home screen launcher | Authentication |
| Analog clock | Backend / API |
| Photo contacts (local) | Database |
| Call confirmation flow | AI / voice assistant |
| Camera intent | Caretaker web portal |
| WhatsApp intent | Remote configuration |
| SOS hold-to-activate | State management framework |
| Caretaker FAB (email only) | Android architecture components |
| Add / edit / remove contacts | |
| More Apps screen | |
| Contacts screen (full grid) | |

---

## Open Questions (to resolve before or during implementation)

1. **Emergency number for SOS** — hardcoded (112) or set by caretaker during setup?
   - Recommendation: default to 112 (India universal emergency), caretaker can change
2. **Maximum contacts** — is 4 on home screen + unlimited on contacts screen the right limit?
3. **Clock numerals** — pure tick marks (fully non-literate) or include 12/3/6/9?
   - Recommendation: tick marks only for Phase 1, caretaker can enable numerals in Phase 2
4. **WhatsApp fallback** — if WhatsApp not installed, show Phone instead?
5. **Contact photo storage** — copy photo into app's private storage or use URI reference?
   - Recommendation: copy into private storage — URI references break if source is deleted

---

## Observations and Concerns

### Concern: Accidental SOS triggers
Mitigated by hold-to-activate. But if the user falls and the phone is pressed against something for 3 seconds, SOS could trigger unintentionally.
Possible future mitigation: require two-zone simultaneous press, or add a cancel screen with a 5-second window after trigger.

### Concern: User getting stuck on a sub-screen
Every sub-screen has an explicit ← back button. The home screen is always reachable.
The system back gesture / button also works as a secondary escape.

### Concern: Caretaker email reliability
Email has latency — not suitable for genuine emergencies. The caretaker button is for non-emergency help requests ("I can't figure out how to use the camera").
SOS handles genuine emergencies via phone call, which is instant and reliable.

### Concern: Add Contact flow complexity
The flow requires a caretaker or helper to complete in Phase 1. The user cannot add contacts independently without literacy.
Phase 3 voice features will allow adding a contact by holding the phone up to someone and saying their name in Dogri/Hindi.

### Concern: WhatsApp dependency
WhatsApp is near-universal in India, but it requires a smartphone and active account.
If WhatsApp is not installed, the button should gracefully fall back to SMS or simply hide.

### Observation: Dogri/Hindi voice layer is critical for long-term usability
Even with perfect visual design, the user will feel more confident if the phone speaks to her in Dogri.
This is a Phase 3 feature but the design is intentionally structured so every button has an associated audio slot — this can be populated in Phase 2 by caretaker-recorded audio.

### Observation: This design works for more than one user profile
The design decisions made for a non-literate Dogri-speaking grandmother also benefit:
- Elderly users with vision decline
- Users with cognitive impairments
- Users in any language/script not supported by standard launchers
- Anyone experiencing their first smartphone

---

## Implementation Order (Phase 1)

```
Step 1  — Project setup, manifest, base theme
Step 2  — Home screen layout scaffold
Step 3  — Analog clock (custom View)
Step 4  — Contact cards + local data model
Step 5  — Call confirmation flow
Step 6  — Camera and WhatsApp intents
Step 7  — SOS hold interaction + vibration + progress ring
Step 8  — Caretaker FAB (all screens, fade behaviour, email intent)
Step 9  — Contacts screen + Add Contact flow
Step 10 — More Apps screen
```

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin | Modern Android standard |
| Min SDK | API 26 (Android 8.0) | 95%+ device coverage |
| UI | XML Layouts + Views | Precise control for custom clock and SOS ring; Compose has gaps for these |
| Storage | SharedPreferences + internal file storage | No backend needed for Phase 1 |
| Libraries | None | Zero third-party dependencies — small APK, no supply chain risk |

---

*Last updated: Phase 1 kickoff*
*Next: Implementation begins at Step 1 — project setup*
