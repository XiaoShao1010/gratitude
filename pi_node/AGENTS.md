# pi_node — Raspberry Pi Central Hub

**Type:** Central controller (Raspberry Pi)
**Role:** LLM intent recognition + audio I/O + navigation orchestration

## OVERVIEW
Raspberry Pi central node running MiniMind (26M) LLM for intent recognition. Handles ASR/TTS audio interaction, TCP communication with MaixCam, and Bluetooth commands to Android phone.

## STRUCTURE
```
pi_node/
├── main.py                      # Entry point (TCP client + audio + LLM + BT)
├── config.py                    # Configuration (IPs, ports, model path)
├── core/
│   ├── llm_engine.py            # MiniMind 26M LLM + tokenizer
│   ├── network_client.py        # TCP client to MaixCam (retry loop)
│   ├── audio_engine.py          # TTS (espeak), ASR placeholder
│   ├── navigator.py             # Intent orchestration (find/navigate/chat)
│   └── phone_comm.py            # Bluetooth RFCOMM server; phone connects in
├── models/
│   ├── intent_brain_26m_512.pth       # MiniMind 26M weights
│   ├── model_minimind.py               # Model config wrapper
│   └── MiniMind2/                       # Full MiniMind 2 tokenizer
```

## WHERE TO LOOK
| Task | File | Notes |
|------|------|-------|
| LLM integration | `core/llm_engine.py` | MiniMind 26M, cached model, torch CPU |
| TCP Client | `core/network_client.py` | Retry loop, daemon thread |
| Audio I/O | `core/audio_engine.py` | espeak TTS (async), ASR TODO |
| Intent orchestration | `core/navigator.py` | find/navigate/chat intent routing |
| BT comm with phone | `core/phone_comm.py` | Standard library RFCOMM socket + BlueZ SDP, JSON protocol |
| Config | `main.py` | Hardcoded IPs: 10.43.210.1:8080 (MaixCam), BT name |

## CONVENTIONS
- **Log prefix**: `[LLM]`, `[Network]`, `[BT]`, `[Audio]`, `[TTS]`
- **Step comments**: `# 数字.` pattern (used in all Python files)
- **Model loading**: Cached in LLMEngine — never reload on request
- **Threading**: Network/BT use daemon threads for async retry loops

## ANTI-PATTERNS
- ❌ Load model on every request (LLMEngine caches it)
- ❌ Block on audio in main loop (use daemon threads)
- ❌ Hardcode IPs in cam_node (Pi side hardcodes 10.43.210.1 for MaixCam USB ethernet)

## COMMANDS
```bash
# Run on Raspberry Pi
python3 main.py
```

## NOTES
- TCP Master → MaixCam TCP Slave (10.43.210.1:8080)
- Android phone initiates Bluetooth RFCOMM connection to Pi server (UUID 00001101-0000-1000-8000-00805F9B34FB); Pi registers the SPP service via `sdptool`
- Protocol from phone: `{"status": "NAV_ACTIVE", "event": "TURN_LEFT", "distance": 15}`
- Protocol to phone: `{"action": "START_NAV", "target": "文三路地铁站"}`
- Intent types: `find_object`, `navigate`, `chat`
- TTS via espeak (zh voice, background `&`)
- MiniMind 26M: Fast CPU inference, suitable for Raspberry Pi 3-4
