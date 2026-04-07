# PROJECT KNOWLEDGE BASE

**Generated:** 2026-04-01
**Project:** Blind_Nav_Assistant (视障导航助手)

## OVERVIEW
Distributed edge AI navigation system for visually impaired. Three nodes: MaixCam (edge vision), Raspberry Pi (LLM hub), Android phone (navigation app).

## STRUCTURE
```
gratitude/
├── cam_node/           # MaixCam edge device (YOLO vision)
├── pi_node/            # Raspberry Pi central hub (LLM + audio)
├── phone_agent/        # Android navigation app (Gaode Maps)
├── docs/               # Technical docs
├── test/               # Manual TCP test server
├── pyproject.toml      # Python package config (broken — see NOTES)
└── README.md
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| MaixCam vision | `cam_node/` | YOLO11 detection, TCP server |
| Raspberry Pi hub | `pi_node/` | MiniMind LLM, audio I/O, TCP client |
| Android navigation | `phone_agent/` | Gaode Maps SDK, Socket service |
| Docs | `docs/GEMINI.md` | Full architecture + conventions |
| Build package | `pyproject.toml` | setuptools build |

## CODE MAP
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `main` | entry | `cam_node/main.py` | MaixCam entry point |
| `main` | entry | `pi_node/main.py` | Raspberry Pi entry point |
| `VisionDetector` | class | `cam_node/core/vision_detector.py` | YOLO11 detection + spatial |
| `NetworkServer` | class | `cam_node/core/network_server.py` | TCP server (MaixCam side) |
| `Interactor` | class | `cam_node/core/interactor.py` | Target spatial feedback |
| `LLMEngine` | class | `pi_node/core/llm_engine.py` | MiniMind 26M intent parsing |
| `NetworkClient` | class | `pi_node/core/network_client.py` | TCP client (Pi side) |
| `AudioEngine` | class | `pi_node/core/audio_engine.py` | espeak TTS |
| `Navigator` | class | `pi_node/core/navigator.py` | Intent orchestration |
| `PhoneComm` | class | `pi_node/core/phone_comm.py` | Bluetooth comm with phone |
| `GaodeNaviManager` | class | `phone_agent/.../navi/GaodeNaviManager.java` | Gaode SDK navigation |
| `SocketServerService` | class | `phone_agent/.../navi/SocketServerService.java` | BT RFCOMM server (foreground service) |

## CONVENTIONS (THIS PROJECT)
- **Naming**: `snake_case` for Python vars/functions; `PascalCase` for Java classes
- **Performance**: Maintain 30-40 FPS on MaixCam, minimize heavy CPU ops in main loop
- **Hardware**: Use `maix` library exclusively on MaixCam for hardware-accelerated tasks
- **IP**: Use `0.0.0.0` for flexibility on edge device; Pi hardcodes `10.43.210.1` for MaixCam USB ethernet
- **Step comments**: `// 数字.` pattern in Java, `# 数字.` in Python (e.g., `// 1. 初始化` or `# 1. 初始化`)

## ANTI-PATTERNS (THIS PROJECT)
- ❌ Hardcode specific IPs in cam_node (use `0.0.0.0`)
- ❌ Commit model files (`.cvimodel`, `.mud`, `.pth`, `.bin`, `.onnx`, `.safetensors` — gitignored)
- ❌ Commit MiniMind2 weights (gitignored with `!` exception)
- ❌ Load LLM model on every request (cache it — pi_node/core/llm_engine.py)
- ❌ Block on network in MaixCam main loop

## COMMANDS
```bash
# Build package
pip install build && python -m build

# Run MaixCam
python3 cam_node/main.py

# Run Raspberry Pi
python3 pi_node/main.py

# Manual TCP test
python3 test/test_server.py
```

## NOTES
- MaixCam ↔ Raspberry Pi: TCP over USB ethernet (MaixCam=server, Pi=client)
- Raspberry Pi ↔ Android: Bluetooth SPP + Socket for navigation commands
- Android navigation: Gaode Maps SDK (geocoding + walking navigation)
- **pyproject.toml broken**: `gratitude-run = "main:main"` points to non-existent root module — actual code is in cam_node/ and pi_node/ as separate hardware deployments
- No CI/CD, no formal test framework (manual testing only)
- Multi-language: Python (nodes) + Java/Kotlin (Android) + XML (layouts)
- phone_agent uses standard Android Gradle project structure
