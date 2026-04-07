# phone_agent — Android Navigation App

**Type:** Android application (standard Gradle project)
**Role:** Navigation UI + Gaode Maps integration + Bluetooth/Socket comm with Raspberry Pi

## OVERVIEW
Android app that receives navigation commands from Raspberry Pi via Bluetooth RFCOMM, performs geocoding + walking navigation via Gaode Maps SDK 9.8.0, and reports navigation events back to Pi.

## STRUCTURE
```
phone_agent/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/blindnav/agent/
│       │   ├── MainActivity.java         # Main activity, fragment navigation
│       │   ├── LoginActivity.java        # Login screen
│       │   ├── HomeFragment.java         # Home tab
│       │   ├── MapFragment.java          # Map display tab
│       │   ├── MyFragment.java          # User profile tab
│       │   ├── UserSessionManager.java   # SharedPreferences session
│       │   └── navi/
│       │       ├── GaodeNaviManager.java     # Gaode SDK wrapper
│       │       └── SocketServerService.java  # BT RFCOMM client (foreground service)
│       └── res/
│           ├── layout/           # XML layouts
│           ├── values/           # strings, colors, styles
│           ├── drawable/         # backgrounds, icons
│           └── menu/             # bottom navigation menu
```

## WHERE TO LOOK
| Task | File | Notes |
|------|------|-------|
| BT client | `navi/SocketServerService.java` | RFCOMM client, foreground service |
| Navigation | `navi/GaodeNaviManager.java` | Gaode SDK, geocoding, navi engine |
| Fragment nav | `MainActivity.java` | Bottom nav, fragment switching |
| Session | `UserSessionManager.java` | SharedPreferences wrapper |

## CONVENTIONS
- **Language**: Java (Android)
- **Package**: `com.blindnav.agent`
- **Naming**: `PascalCase` for classes, `camelCase` for methods/vars
- **Step comments**: `// 数字.` pattern (e.g., `// 1. 初始化地理编码引擎`) — Chinese numbering, matches Python nodes
- **Logging**: `Log.d(TAG, msg)` / `Log.e(TAG, msg, e)` with static TAG per class
- **Threading**: BT/network ops on background threads, callbacks on main thread
- **Gaode SDK**: v9.8.0 — geocoding via `GeocodeSearch`, navi via `AMapNavi`

## ANTI-PATTERNS
- ❌ Block on network in main thread
- ❌ Hardcode city name in navigation (currently hardcoded "杭州")
- ❌ Return null from callbacks without error handling

## COMMANDS
```bash
# Build Android APK (requires Android SDK)
cd phone_agent && ./gradlew assembleRelease

# Or in Android Studio: File → Open → phone_agent/
```

## COMMUNICATION PROTOCOL (Bluetooth RFCOMM)
**From Pi → Phone** (Pi sends, phone receives):
- `{"action": "START_NAV", "target": "文三路地铁站"}`
- `{"action": "GET_LOCATION"}`

**From Phone → Pi** (phone responds):
- `{"status": "OK", "event": "CURRENT_LOCATION", "latitude": ..., "longitude": ..., "address": "..."}`
- `{"status": "NAV_ACTIVE", "event": "TURN_LEFT", "distance": 15}`
- `{"status": "ARRIVED"}`

## NOTES
- Runs as **foreground service** with notification — survives app backgrounding
- Pi acts as BT server, phone acts as BT client (RFCOMM)
- UUID: `00001101-0000-1000-8000-00805F9B34FB`
- GaodeNaviManager: LocationSnapshot pattern for async callbacks
- Hardcoded city "杭州" in `SocketServerService.handleRequest` — TODO: make dynamic
- App uses `UserSessionManager` with SharedPreferences for login state