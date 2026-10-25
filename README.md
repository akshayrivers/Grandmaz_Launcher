# Grandma's Launcher

An Android home screen for a grandmother who does not read, has low vision, and has a hand tremor. She finds people by their photos, the clock, and colour. Family caretakers set the phone up and watch it from a separate web app. The phone and the caretakers talk only through the backend.

## The three repositories

| App | Repository |
|---|---|
| This launcher (Kotlin, XML views) | [akshayrivers/Grandmaz_Launcher](https://github.com/akshayrivers/Grandmaz_Launcher) |
| Backend (Fastify, Postgres) | [akshayrivers/Grandmaz_Backend](https://github.com/akshayrivers/Grandmaz_Backend) |
| Caretaker web app | [akshayrivers/Grandmaz_Caretaker](https://github.com/akshayrivers/Grandmaz_Caretaker) |

On a local machine the siblings are `sys-launcher`, `dadi's Backend`, and `Dadi's Caretaker`. API fields are camelCase.

## Build, install, test

JDK 21. From this directory:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
```

`installDebug` needs a device or emulator. `compileSdk` and `targetSdk` are 36. `minSdk` is 26. The app does not request `QUERY_ALL_PACKAGES`.

Run the backend from its own repository with `npm run dev` (port 3000) before pairing. The launcher stores the backend base URL in preferences. Use HTTPS for anything other than a machine you control. Do not commit `local.properties`, keystores, or `google-services.json`.

## Setup and pairing

1. Install the launcher and complete the on-phone setup. A helper enters the caretaker's email. The phone shows a pairing code. There is no Google sign-in on grandma's phone.
2. The caretaker opens the magic link on their own device, signs in, and accepts. The first caretaker to link is the primary. There is exactly one primary.
3. The caretaker PIN is created on grandma's phone. It never goes to the server, a snapshot, or a remote command.

After that, the phone pulls a versioned config document (people, weather city, reminders, voice clips) and caretaker edits are saved back with `If-Match`.

## Rules that must not be regressed

- Photos, icons, colour, position, and the analog clock come before words. The clock has tick marks and no numerals.
- One action on a grandma-facing screen. Touch targets are at least 80dp, and primary actions are at least 100dp.
- No hidden gestures for her actions. A long-press is only for a helper (the clock long-press opens caretaker mode after the PIN).
- Every sub-screen has a house control, and a quiet period returns to home.
- Red `#C0392B` is SOS only. Blue is call. Green is WhatsApp or yes. Amber is the caretaker. The background is warm off-white `#F7F3EE`.
- The theme stays light.
- Do not add Toasts as feedback for grandma. Speak, show a picture, or vibrate.
- English on screen is for the helper. Her labels are spoken: a recorded clip, then Hindi TTS, then English, then a vibration. Dogri is a recorded clip only.
- SOS is a 3 second hold. The phone then calls the primary caretaker immediately (`ACTION_CALL`, then `ACTION_DIAL`). Alerts with location go to every caretaker. Stopping alerts does not hang up. If the alert cannot be posted and SMS fallback is on, the phone texts a map link. Location is used only while SOS is active.
- Home shows four pinned photos plus a way to the rest. WhatsApp video is to one person, then a WhatsApp voice call, then a normal call.
- Weather is Open-Meteo: the caretaker's city first, then a coarse location, cached for about 45 minutes.
- The server is the intermediary. This app does not open a live view of the screen and does not remote-control the phone.
- No API key in the APK. The assistant asks the backend for a session. The phone does not upload her audio.

## Features

Screens from a local-debug run on a Redmi Note 8 Pro (Android 11). The debug app used the laptop backend on the same Wi-Fi. The release build still uses `https://grandmaz-backend.onrender.com/`.

- [Home](docs/features/home.png) — analog clock, weather card, people, Gallery, YouTube, and the floating Home and Ask for help controls.
- [Alarm setter](docs/features/alarm-setter.png) — hour and minute hands, with the sun and moon.
- [Alarms](docs/features/alarms.png) — the alarm list. None were set on this run.
- [Ask for help](docs/features/ask-for-help.png) — the help picture, with Edit home and weather underneath.
- [Weather](docs/features/weather.png) — the Mausam screen. This run showed a live Open-Meteo temperature.
- [People](docs/features/contacts.png) — the people grid.
- [More apps](docs/features/more-apps.png) — empty until a helper pins apps.
- [Ask](docs/features/ask.png) — hold the microphone, then let go.
- [Record her words](docs/features/record-clips.png) — helper screen after a long-press on the home clock.
- [Add a caretaker](docs/features/add-caretaker.png) — weather city, the Gemini model id, and the invitation form. No invitation was sent.

The caretaker PIN dialog did not appear on this phone, because no PIN is set. Camera, WhatsApp, and SOS were left closed.

## Not done

- Live screen viewing and remote control are not in this app.
- Ask uses Gemini through a short-lived token from the backend. The phone has no Gemini key. Without `GEMINI_API_KEY` on the server, Ask says the helper is not available.
- Firebase Cloud Messaging is not wired. The phone syncs on its own schedule rather than waking from a push.
- Web push for caretakers is not configured.
