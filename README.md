<div align="center">

# ⚡ Relay Control

**A native Android dashboard for your ESP32 4-channel relay controller.**
Finds your devices on its own, switches relays in one tap, and wears a dark/light neumorphic skin with orange accents.

[![Android CI](https://github.com/OWNER/REPO/actions/workflows/android.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/android.yml)
![Platform](https://img.shields.io/badge/platform-Android%209%2B-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-28-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Hardware](https://img.shields.io/badge/hardware-ESP32-E7352C?logo=espressif&logoColor=white)
![Dependencies](https://img.shields.io/badge/runtime%20deps-3-FF8A2B)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

</div>

---

## ✨ Overview

Relay Control talks to the HTTP API of an ESP32 relay board (4 channels, firmware in a separate repo). Open the app and it
discovers controllers on your Wi-Fi, lists every switch with its full name, and lets you toggle, rename, reorder and
schedule them.

| | |
|---|---|
| 🔍 **Auto-discovery** | Scans your private Wi-Fi subnet on launch, no IP typing. Manual add and manual rescan are available too. |
| 🎛️ **Big, friendly tiles** | One full-width tile per switch. Tap anywhere on the tile to toggle, with haptic feedback. |
| 🌗 **Dark & light** | Follows the system theme. Neumorphic surfaces, orange accent shades. |
| ↕️ **Custom order** | Reorder switches per device; the order is remembered. |
| ⏰ **Schedules** | Daily on/off times per relay, shown on the tile only when enabled. |
| 🔐 **API token** | Sent as `X-API-Key`. Enter it, **scan a QR** to import it, or **share a QR** with someone you trust. |
| 🗣️ **Voice & automation** | Launcher shortcuts and a `relay://` command link for Tasker, MacroDroid, NFC tags and assistants. |
| 🛡️ **Gentle on your network** | Rate-limited scans, backoff, nothing sent while the app is in the background. |

## 🖼️ Screenshots

> Add your screenshots here.
>
> | Dark | Light |
> |---|---|
> | `docs/dark.png` | `docs/light.png` |

## 🚀 Getting started

### Requirements
- Android Studio (Koala or newer) **or** a terminal
- **JDK 17 or 21.** Newer JDKs (25/26) break the Kotlin 1.9 compiler and Android lint.
- An Android 9+ phone on the same Wi-Fi as the controller
- Google Play services on the phone (only needed for QR scanning)

### Build
```bash
git clone https://github.com/OWNER/REPO.git
cd REPO
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # smaller, shrunk APK: app/build/outputs/apk/release/
```
The release build is signed with the debug key so it installs on your own phone. Use your own keystore if you publish it.

### First run
1. Open the app. It searches your network and lists every controller it finds.
2. If a device shows **🔑 locked**, tap the key and either type the token, **Scan QR**, or paste it.
3. Tap a tile to switch a relay. Tap ⚙ for the name and schedule.

## 🔐 Token & QR

| Action | How |
|---|---|
| Default token | 🔑 in the top bar. Used for every device without its own token. |
| Per-device token | 🔑 on the device page. |
| Import | **Scan QR**. Accepts `relay://pair?key=<token>&host=<ip>` or a bare token (16+ characters). |
| Share | **Share QR** shows the token as a QR code. Anyone who scans it can control your relays. |

The token is stored in app-private storage, sent only in a header, and never logged or put in a URL. The device speaks plain
HTTP, so keep it on a trusted LAN and never port-forward it.

## 🗣️ Voice, shortcuts & automation

Long-press the app icon for **All OFF** and **Toggle <name>** shortcuts, or drag them to your home screen.

Automation apps can fire a link at the app's invisible command screen:

```
relay://cmd?name=Porch%20Light&state=on          # on | off | toggle
relay://cmd?name=all&state=off
relay://cmd?name=Water%20Pump&state=toggle&device=192.168.1.250
```

```bash
adb shell "am start -a android.intent.action.VIEW -d 'relay://cmd?name=Porch%20Light&state=on' com.relay.ctrl"
```

Names are matched ignoring case and punctuation. If a name matches more than one switch, nothing happens and the app says so.
Gemini's app-integration API (AppFunctions) is not wired up yet.

## 🧱 How it works

```
┌────────────┐   X-API-Key / HTTP :80    ┌──────────────────┐
│ Android UI │ ───────────────────────▶ │ ESP32 relay board│
│  (Compose) │ ◀─────────────────────── │  /api/status …   │
└─────┬──────┘        JSON               └──────────────────┘
      │
 ViewModel ── polls online devices every 3 s, offline ones every 15 s
      │
 Api / Tokens / Commander / Shortcuts
```

| File | Role |
|---|---|
| `Api.kt` | HTTP client, discovery, verified writes (every change is re-read from `/api/status`) |
| `Tokens.kt` | Default and per-device API tokens |
| `Vm.kt` | State, polling, backoff, ordering |
| `Ui.kt`, `Neu.kt` | Screens and the neumorphic design system |
| `TokenUi.kt` | Token dialog, QR scan and share |
| `Commander.kt`, `CommandActivity.kt`, `Shortcuts.kt` | Voice and automation entry points |

### Network behaviour
- **Light check** of known devices plus `<subnet>.250`; a **full /24 sweep** only the first time nothing is known, or when you tap the radar.
- Full sweeps run at most once per 30 s, 12 requests at a time, and **only on private Wi-Fi** (never mobile data or VPN).
- Rediscovery backs off from 15 s up to 5 min. Nothing is sent while the app is in the background.
- Write calls run one at a time per device, matching the firmware's single-threaded server.

## 🔌 Device API (what the app uses)

`GET /api/status` · `/api/info` · `/api/set` · `/api/all` · `/api/rename` · `/api/schedule`, all with the `X-API-Key` header.
The firmware's OTA endpoints are deliberately not used. Schedules only run once the device clock is synced; the app warns
when it isn't.

## 🛠️ Tech stack

Kotlin · Jetpack Compose (Material 3) · Coroutines and StateFlow · `HttpURLConnection` · ZXing (QR encode) · Google code scanner (QR decode)

## 🗺️ Roadmap

- [ ] Gemini AppFunctions integration
- [ ] Drag-and-drop reordering
- [ ] Home-screen widget
- [ ] OTA update status (read-only)

## 🤝 Contributing

Issues and pull requests are welcome. Please make sure `./gradlew assembleRelease` passes; CI runs the same command.

## 📄 License

MIT. Replace `OWNER/REPO` in the badges and links above with your repository path.
