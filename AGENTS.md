# PROJECT KNOWLEDGE BASE

**Generated:** 2026-03-19
**Project:** Blind_Nav_Assistant (视障导航助手)

## OVERVIEW
Distributed edge AI navigation system for visually impaired. MaixCam (edge vision) + Raspberry Pi (central hub with LLM).

## STRUCTURE
```
gratitude/
├── cam_node/          # MaixCam edge device (YOLO vision)
├── pi_node/           # Raspberry Pi central hub (LLM + audio)
├── docs/              # Technical docs
├── test/              # Manual TCP test server
├── pyproject.toml     # Python package config
└── README.md
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| MaixCam vision | `cam_node/` | YOLO detection, TCP server |
| Raspberry Pi hub | `pi_node/` | LLM, audio I/O, TCP client |
| Docs | `docs/GEMINI.md` | Full architecture + conventions |
| Build package | `pyproject.toml` | setuptools build |

## CODE MAP
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `main` | entry | `cam_node/main.py` | MaixCam entry point |
| `main` | entry | `pi_node/main.py` | Pi entry point |

## CONVENTIONS (THIS PROJECT)
- **Naming**: `snake_case` for Python vars/functions
- **Performance**: Maintain 30-40 FPS, minimize heavy CPU ops in main loop
- **Hardware**: Use `maix` library exclusively for hardware-accelerated tasks
- **IP**: Use `0.0.0.0` for flexibility, avoid hardcoding

## ANTI-PATTERNS (THIS PROJECT)
- ❌ Hardcode specific IPs (use `0.0.0.0`)
- ❌ Commit model files (`.cvimodel`, `.mud`, `.pth`, `.bin`, `.onnx`, `.safetensors` gitignored)
- ❌ Commit MiniMind2 weights (gitignored with `!` exception)

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
- Two independent nodes communicate via TCP (192.168.233.1:12345)
- MaixCam acts as TCP Server, Raspberry Pi as TCP Client
- No CI/CD, no formal test framework (manual testing only)
