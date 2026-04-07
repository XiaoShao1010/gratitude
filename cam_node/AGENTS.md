# cam_node — MaixCam Edge Vision Node

**Type:** Edge device (Sipeed MaixCam K230)
**Role:** Real-time YOLO obstacle detection + spatial positioning

## OVERVIEW
MaixCam edge node running MaixPy v3. Performs YOLO11-n detection, monocular distance estimation, and TCP communication with Raspberry Pi hub.

## STRUCTURE
```
cam_node/
├── main.py                  # Entry point
├── config.py                # Configuration (camera/model paths)
├── core/
│   ├── vision_detector.py   # YOLO11 detection + spatial math
│   ├── network_server.py    # TCP server (0.0.0.0:8080)
│   └── interactor.py        # Target spatial feedback (direction, distance)
└── models/                  # YOLO model files (.mud, .cvimodel)
```

## WHERE TO LOOK
| Task | File | Notes |
|------|------|-------|
| YOLO detection | `core/vision_detector.py` | YOLO11-n, obstacle whitelist, priority colors |
| TCP Server | `core/network_server.py` | Protocol server, recv loop in daemon thread |
| Distance estimation | `core/interactor.py` | Monocular ranging via focal length + real heights |
| Config | `config.py` | MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS |

## CONVENTIONS
- **Log prefix**: `[Vision]`, `[Network]`, `[MaixCam Node]`
- **Step comments**: `# 数字.` pattern (e.g., `# 1. 初始化`) in all Python files
- **Status output**: Emoji in print statements (⏳, ✅, etc.)
- **Frame rate**: Target 30-40 FPS — no heavy ops in main loop

## ANTI-PATTERNS
- ❌ Hardcode camera resolution
- ❌ Heavy CPU ops in main loop (target 30-40 FPS)
- ❌ Block on network in main loop
- ❌ Hardcode IPs — use `0.0.0.0`

## COMMANDS
```bash
# Deploy to MaixCam — upload entire cam_node/ folder, then:
python3 main.py
```

## NOTES
- Runs on MaixCam hardware with MaixPy v3
- YOLO model: YOLO11-n, optimized for K230 KPI
- Default model path: `/root/models/`
- USB Type-C connection to Raspberry Pi
- Two modes: **Safe mode** (report P1 obstacles ~2Hz) and **Search mode** (highlight target object)
- TCP Protocol: JSON frames — `{"type": "search", "object": "..."}` or `{"type": "warning", "object": "...", "distance": ...}`
