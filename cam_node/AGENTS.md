# cam_node — MaixCam Edge Vision Node

**Type:** Edge device (Sipeed MaixCam K230)
**Role:** Real-time YOLO obstacle detection + spatial positioning

## OVERVIEW
MaixCam edge node running MaixPy v3. Performs YOLO11-n detection, monocular distance estimation, and TCP communication with Raspberry Pi hub.

## STRUCTURE
```
cam_node/
├── main.py              # Entry point
├── config.py            # Configuration
├── requirements.txt     # Dependencies (empty)
├── core/
│   ├── vision_detector.py   # YOLO detection
│   ├── network_server.py    # TCP server
│   └── interactor.py        # Interaction logic
└── models/              # YOLO model files (.mud, .cvimodel)
```

## WHERE TO LOOK
| Task | File | Notes |
|------|------|-------|
| YOLO detection | `core/vision_detector.py` | YOLO11-n, obstacle classes |
| TCP Server | `core/network_server.py` | Protocol server, port 12345 |
| Distance estimation | `core/vision_detector.py` | Monocular ranging |
| Config | `config.py` | Camera/model paths |

## CONVENTIONS
- **Log prefix**: `[Vision]`, `[MaixCam Node]`
- **Step comments**: `# 数字.` pattern (e.g., `# 1. 初始化`)
- **Status output**: Emoji in print statements

## ANTI-PATTERNS
- ❌ Hardcode camera resolution
- ❌ Heavy CPU ops in main loop (target 30-40 FPS)
- ❌ Block on network in main loop

## COMMANDS
```bash
# Deploy to MaixCam
# Upload entire cam_node/ folder, then:
python3 main.py
```

## NOTES
- Runs on MaixCam hardware with MaixPy v3
- YOLO model: YOLO11-n, optimized for K230 KPI
- Default model path: `/root/models/`
- USB Type-C connection to Raspberry Pi
