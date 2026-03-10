import os

class AudioEngine:
    def __init__(self):
        print("[Audio] Initializing Audio Engine (espeak)")

    def speak(self, text):
        """TTS 语音播报"""
        print(f"[TTS] {text}")
        # 在树莓派上使用异步 espeak 播报
        os.system(f'espeak -v zh "{text}" &')

    def listen(self):
        """(预留) ASR 语音转文字逻辑"""
        # TODO: 集成 VAD 与语音识别 (如 Whisper 或其他 ASR 引擎)
        return ""
