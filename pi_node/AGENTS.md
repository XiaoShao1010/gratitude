# pi_node — Raspberry Pi Central Hub

**Type:** Central controller (Raspberry Pi)
**Role:** LLM intent recognition + audio I/O + orchestration

## OVERVIEW
Raspberry Pi central node running MiniMind (26M) LLM for intent recognition. Handles ASR/TTS audio interaction and TCP communication with MaixCam.

## STRUCTURE
```
pi_node/
├── main.py                  # Entry point
├── requirements.txt         # Dependencies (empty)
├── core/
│   ├── llm_engine.py        # MiniMind LLM integration
│   ├── network_client.py    # TCP client
│   └── audio_engine.py      # ASR/TTS (espeak)
├── models/
│   ├── intent_brain_26m_512.pth   # MiniMind weights
│   ├── model_minimind.py           # Model wrapper
│   └── MiniMind2/                   # Full MiniMind 2 model
```

## WHERE TO LOOK
| Task | File | Notes |
|------|------|-------|
| LLM integration | `core/llm_engine.py` | MiniMind 26M intent brain |
| TCP Client | `core/network_client.py` | Connect to MaixCam |
| Audio I/O | `core/audio_engine.py` | ASR listening, TTS espeak |
| Intent brain | `models/intent_brain_26m_512.pth` | 26M params, 512 context |

## CONVENTIONS
- **Log prefix**: `[LLM]`, `[Network]`
- **Step comments**: `# 数字.` pattern
- **Model loading**: Lazy load on first use

## ANTI-PATTERNS
- ❌ Load model on every request (cache it)
- ❌ Block on audio in main loop
- ❌ Hardcode MaixCam IP (use `0.0.0.0` discovery)

## COMMANDS
```bash
# Run on Raspberry Pi
python3 main.py
```

## NOTES
- Acts as TCP Master, MaixCam as TCP Slave
- Protocol: Binary frames with CRC16
- TTS via espeak
- MiniMind 26M: Fast inference, suitable for Raspberry Pi
